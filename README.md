# Spring Batch 예제 코드

## 스프링 배치 (Spring Batch)
>일괄 처리를 위한 오픈소스 프레임 워크
>
> 여러 Job을 순차적으로 처리한다.

보통 어떤 경우에 사용할까?
1. 대용량의 비즈니스 데이터를 복잡한 작업으로 처리해야하는 경우

2. 특정한 시점에 스케쥴러를 통해 자동화된 작업이 필요한 경우 (ex. 푸시알림, 월 별 리포트)

3. 대용량 데이터의 포맷을 변경, 유효성 검사 등의 작업을 트랜잭션 안에서 처리 후 기록해야하는 경우

---

## 스프링 배치 시나리오
![시나리오](https://velog.velcdn.com/images/dlwp7581/post/ffb42a5e-4ef8-4273-8020-14d478b0bdbb/image.jpeg)

1. 읽기 → 데이터 저장소(데이터베이스, 파일 등) 에서 데이터를 읽어온다.
2. 처리 → 데이터를 원하는 대로 가공/처리 한다. 
3. 쓰기 → 처리 완료된 데이터를 저장한다.

---

## 스프링 배치 관계도
![스프링 배치 관련 객체 관계](https://velog.velcdn.com/images/dlwp7581/post/0b7a9950-5abb-469a-806a-efd690d726e9/image.png)

- Job과 Step은 1:M의 연관 관계를 맺고 있다.
- Step와 ItemReader, ItemProcessor, ItemWriter들은 각각 1:1의 연관 관계를 맺고 있다.

> ### Job
> job은 하나의 작업이다.
> 
> 작업은 여러 단계로 구분된다.
> 
> 여러 단계의 모임이다.

> ### Step
> step은 단일 단계을 말한다.
> 
> 일반적으로 Chunk 지향 프로세싱을 이용한다.
> 
> > Chunk 지향 프로세싱
> >
> > 한 번에 하나씩 데이터를 읽어 Chunk 덩어리를 만들어 이 덩어리 단위로 트랜잭션을 다루는 것
> >
> > 수행 중 실패할 경우 해당 Chunk만큼 콜백이 되고 이전에 커밋 된 트랜잭션 범위까지는 반영
> >
> > ![스프링 배치 청크 지향 프로세싱](https://velog.velcdn.com/images/dlwp7581/post/e6040892-73c2-4e23-b0de-dbf33828efeb/image.png)
> > Reader, Processor에서는 1건씩 다루고, Writer에서는 Chunk 단위로 일괄 처리한다.
> 
> Reader, Processor, Writer를 이용하여 구성된다.

---

**Person**
```java
package com.example.batch.model;

public class Person {
    private String lastName;
    private String firstName;
 
    // Constructor, getter, setter, toString...
}
```

읽어온 데이터로 구성될 객체

**BatchConfiguration**
```java
package com.example.batch.config;

// ...
public class BatchConfiguration {
    // ...

    @Bean
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader") // (1)
                .resource(new ClassPathResource("sample-data.csv")) // (2)
                .delimited()
                .names(new String[]{"firstName", "lastName"}) // (3)
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(Person.class);
                }}) // (4)
                .build();
    }
    
    // ...
}
```
ItemReader을 생성한다.
> FlatFileItemReader은 DB가 아닌 Resource에서 데이터를 읽어오는 구현체이다.

(1) ExecutionContext의 내부 키를 계산하는 데 사용되는 이름을 입력한다.

(2) Resource 파일을 찾는다.

(3) 형식에 맞춰 데이터를 잘라낸다.

(4) 잘라낸 데이터를 객체로 생성한다.

```java
package com.example.batch.config;

// ...
public class BatchConfiguration {
    // ...

    @Bean
    public PersonItemProcessor processor() {/* ... */} // (1)
    
    // ...
}
```
ItemProcessor을 생성한다.

여기서는 직접 구현한 **PersonItemProcessor**를 사용했다.
> processor는 ItemProcessor<I, O>인터페이스를 상속받아 작성한다.

(1) 구현한 처리기를 스프링 빈에 등록시킨다.

```java
package com.example.batch.config;

// ...
public class BatchConfiguration {
    // ...

    @Bean
    public JdbcBatchItemWriter<Person> write(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>() // (1)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource) 
                .build();
    }

    // ...
}
```
ItemWriter를 생성한다.
> 데이터베이스에 데이터를 쓰기 위해 Jdbc를 사용한다.
> 
> JdbcBatchItemWriter은 쿼리를 ChunkSize만큼 쌓아 한번에 DB로 전송한다.

(1) jdbc 사용을 위한 ItemWriter이다.

```java
package com.example.batch.config;

// ...
public class BatchConfiguration {
    // ...

    @Bean
    public Job importUserJob(JobCompletionNotificationListener listener, Step step) {
        return jobBuilderFactory.get("importUserJob") // (1)
                .incrementer(new RunIdIncrementer()) // (2)
                .listener(listener) // (3)
                .flow(step) // (4)
                .end()
                .build();
    }

    // ...
}
```
Job을 정의한다.

(1) job builder를 생성하고 해당 job repository를 초기화 한다.
Bean에 등록되어 있는 이름과 작업의 이름이 다를 수 있다.

(2) 동일 Job Parameter로 계속 실행이 될 수 있도록 `run.id` 라는 임의의 파라미터를 추가로 사용해 매번 `run.id`의 값을 변경해준다.

(3) JobExecutionListener를 상속받은 listener를 등록할 수 있다.

(4) step에 따라 작업의 흐름을 제어할 수 있게 한다.

```java
package com.example.batch.config;

// ...
public class BatchConfiguration {
    // ...

    @Bean
    public Step step(JdbcBatchItemWriter<Person> writer) {
        return stepBuilderFactory.get("step") // (1)
                .<Person, Person> chunk(10) // (2)
                .reader(reader()) // (3)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skipLimit(3) // (4)
                .processor(processor()) // (5)
                .writer(writer) // (6)
                .build();
    }
    
    // ...
}
```
Step을 정의한다.

(1) step builder를 생성하고 job repository와 transaction manager를 초기화한다.
Bean에 등록되어있는 이름과 단계 이름이 다를 수 있다.

(2) chunk개수를 지정한다.

(3) ItemReader를 등록한다.

(4) ItemReader로 데이터를 읽을 때 FlatFileParseException 발생 시 지정한 개수만큼 스킵하고 진행한다.

(5) ItemProcessor을 등록한다.

(6) ItemWriter을 등록한다.


**PersonItemProcessor**
```java
package com.example.batch.lib;

// ...
public class PersonItemProcessor implements ItemProcessor<Person, Person> {
    @Override
    public Person process(Person item) throws Exception {
        // ...
    }
}
```

직접 작성한 ItemProcessor이다.

> ItemProcessor인터페이스를 상속 받아야 한다.

오버라이딩 한 process() 함수에 처리 로직을 넣는다.

**JobCompletionNotificationListener**
```java
package com.example.batch.lib;

// ...
public class JobCompletionNotificationListener extends JobExecutionListenerSupport {

    // ...
    
    @Override
    public void afterJob(JobExecution jobExecution) {
        // ...
    }
}
```
직접 작성한 listener 이다.

> listener는 JobExecutionListener를 상속받아야 한다.

JobExecutionListener 인터페이스에는 afterJob()와 beforeJob() 함수가 있다.

하지만 여기서는 afterJob만 필요하므로 JobExecutionListener 인터페이스의 구현체인 JobExecutionListenerSurpport를 상속받는다.
