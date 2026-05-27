package com.example.boot4;

import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AliasFor;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import javax.sql.DataSource;
import java.lang.annotation.*;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@EnableResilientMethods
@ImportHttpServices(CatFactsClient.class)
//@Import(MyBeanRegistrar.class)
@SpringBootApplication
public class Boot4Application {

    public static void main(String[] args) {
        SpringApplication.run(Boot4Application.class, args);
    }

    @Bean
    JdbcPostgresDialect jdbcPostgresDialect() {
        return JdbcPostgresDialect.INSTANCE;
    }
}

class MyBeanRegistrar implements BeanRegistrar {

    @Override
    public void register(BeanRegistry registry,
                         Environment env) {

//        registry.registerBean(MyRunner.class);


        for (var i = 0; i < 5; i++)
            registry.registerBean(MyRunner.class, a -> a
                    .supplier(supplierContext -> new MyRunner(supplierContext.bean(DataSource.class))));
    }
}

// xml, component scanning, java config, ...
// BeanDefinition
//              BeanFactoryPostProcessor
// bean
//              BeanPostProcessor#postProcessBeforeInitialization
//              BeanPostProcessor#postProcessAfterInitialization

//@Frijol
class MyRunner implements ApplicationRunner {


    private final DataSource dataSource;

    MyRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IO.println("Hello, World!");
    }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
@interface Frijol {

    /**
     * Alias for {@link Component#value}.
     */
    @AliasFor(annotation = Component.class)
    String value() default "";

}


@Controller
@ResponseBody
class CatsController {

    private final CatFactsClient catFactsClient;
    private final AtomicInteger atomicInteger = new AtomicInteger(0);

    CatsController(CatFactsClient catFactsClient) {
        this.catFactsClient = catFactsClient;
    }

    @GetMapping("/hi")
    String hi() {
        return "hola!!";
    }

    @ConcurrencyLimit(10)
    @Retryable(maxRetries = 5, includes = IllegalStateException.class)
    @GetMapping("/cats")
    CatFacts facts() {

        if (this.atomicInteger.getAndIncrement() < 5) {
            IO.println("oops!");
            throw new IllegalStateException("Simulated error");
        }
        IO.println("facts");
        return this.catFactsClient.facts();
    }
}

interface CatFactsClient {

    @GetExchange("https://www.catfacts.net/api")
    CatFacts facts();
}

record CatFact(String fact) {
}

record CatFacts(CatFact[] facts) {
}


@Controller
@ResponseBody
class DogsController {

    private final DogRepository dogRepository;

    DogsController(DogRepository dogRepository) {
        this.dogRepository = dogRepository;
    }

    @GetMapping(value = "/dogs", version = "1.1")
    Collection<Dog> dogs() {
        return dogRepository.findAll();
    }

    @GetMapping(value = "/dogs", version = "1.0")
    Collection<Map<String, Object>> dogsv0() {
        return dogRepository.findAll()
                .stream()
                .map(dog -> Map.of("id", (Object) dog.id(), "fullName", dog.name()))
                .toList();
    }
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {

    // select * from dog where name  = ?
    Collection<Dog> findByName(String name);
}

record Dog(@Id int id, String name, String owner, String description) {
}