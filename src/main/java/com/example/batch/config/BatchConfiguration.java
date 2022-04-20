package com.example.batch.config;

import com.example.batch.lib.JobCompletionNotificationListener;
import com.example.batch.lib.PersonItemProcessor;
import com.example.batch.model.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;

@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class BatchConfiguration {
    public final JobBuilderFactory jobBuilderFactory;
    public final StepBuilderFactory stepBuilderFactory;

    /**
     * ItemReader 를 만듬.<br>
     * sample-data.csv 파일을 찾아 구문을 해석한다.
     */
    @Bean
    public FlatFileItemReader<Person> reader() {
        return new FlatFileItemReaderBuilder<Person>()
                .name("personItemReader")
                .resource(new ClassPathResource("sample-data.csv"))
                .delimited()
                .names(new String[]{"firstName", "lastName"})
                .fieldSetMapper(new BeanWrapperFieldSetMapper<>() {{
                    setTargetType(Person.class);
                }})
                .build();
    }

    /**
     * <p>데이터를 대문자로 변환함.<br>
     * PersonItemProcessor 을 생성해서 리턴한다.</p>
     *
     *
     * {@link PersonItemProcessor}
     */
    @Bean
    public PersonItemProcessor processor() {
        return new PersonItemProcessor();
    }

    /**
     * <p>ItemWriter 을 생성한다.<br>
     * JDBC 대상에 초점을 두며 @EnableBatchProcessing 에서 만든 dataSource 의 복사본을 자동으로 가져온다.<br>
     * 한 {@link Person}를 삽입하는데 필요한 sql 문을 포함한다.</p>
     */
    @Bean
    public JdbcBatchItemWriter<Person> write(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Person>()
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("INSERT INTO people (first_name, last_name) VALUES (:firstName, :lastName)")
                .dataSource(dataSource)
                .build();
    }

    /**
     * 작업(job)을 정의함<br>
     * 작업(job)은 각각의 읽기, 처리, 쓰기단계를 통해 생성된다.
     */
    @Bean
    public Job importUserJob(JobCompletionNotificationListener listener, Step step) {
        return jobBuilderFactory.get("importUserJob")
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .flow(step)
                .end()
                .build();
    }

    /**
     * 단일 단계를 정의함.
     * <p>한 번에 쓸 데이터 양을 정의한다.<br>
     * 여기서는 한 번에 최대 10개의 레코드를 기록한다.<br>
     * 이 후 아까 bean 에 등록한 reader, processor, writer 를 구성한다.</p>
     */
    @Bean
    public Step step(JdbcBatchItemWriter<Person> writer) {
        return stepBuilderFactory.get("step")
                // chunk()는 일반 메서드이기 때문에 <Person, Person>을 가진다.
                // 이것은 처리의 각 청크의 입력 및 출력 유형을 나타내며 ItemReader<Person> 및 ItemWriter<Person>과 함께 정렬된다.
                .<Person, Person> chunk(10)
                .reader(reader())
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skipLimit(3)
                .processor(processor())
                .writer(writer)
                .build();
    }

}
