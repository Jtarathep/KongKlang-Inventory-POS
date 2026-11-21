package com.kongklang;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("testshop")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="KONGKLANG_RUN_MYSQL_TESTS",matches="true")
class KongklangApplicationTests {

    @Test
    void contextLoads() {
    }

}
