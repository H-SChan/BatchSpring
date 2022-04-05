package com.example.batch.lib;

import com.example.batch.model.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListenerSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class JobCompletionNotificationListener extends JobExecutionListenerSupport {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 작업이 BatchStatus 가 COMPLETED 일 때까지 대기한다.<br>
     * 완료된 다음 JdbcTemplate 를 사용해 결과를 검사한다.
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("JOB FINISHED!");

            jdbcTemplate.query("SELECT first_name, last_name FROM people", (rs, row) -> new Person(
                    rs.getString(1),
                    rs.getString(2)
            )).forEach(person -> log.info("Found < " + person + " > in the database."));
        }
    }
}
