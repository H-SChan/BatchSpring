package com.example.batch.lib;

import com.example.batch.model.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
public class PersonItemProcessor implements ItemProcessor<Person, Person> {


    @Override
    public Person process(Person item) throws Exception {
        // 이름 대문자로 변환
        String firstName = item.getFirstName().toUpperCase();
        String lastName = item.getLastName().toUpperCase();

        Person transformedPerson = new Person(firstName, lastName);

        log.info(item + " into " + transformedPerson);

        return transformedPerson;
    }
}
