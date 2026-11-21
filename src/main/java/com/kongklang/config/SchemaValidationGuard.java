package com.kongklang.config;
import org.springframework.stereotype.Component;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import java.util.Map;

/** Never let an old ddl-auto=update setting bypass the reviewed SQL migrations. */
@Component
public class SchemaValidationGuard implements HibernatePropertiesCustomizer {
    @Override public void customize(Map<String,Object> properties) {
        properties.remove("jakarta.persistence.schema-generation.database.action");
        properties.remove("javax.persistence.schema-generation.database.action");
        properties.put("hibernate.hbm2ddl.auto","validate");
    }
}
