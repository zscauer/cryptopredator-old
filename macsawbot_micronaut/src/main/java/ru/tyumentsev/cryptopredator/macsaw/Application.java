package ru.tyumentsev.cryptopredator.macsaw;

import io.micronaut.runtime.Micronaut;

public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

//    @Singleton
//    static class ObjectMapperBeanEventListener implements BeanCreatedEventListener<ObjectMapper> {
//
//        @Override
//        public ObjectMapper onCreated(BeanCreatedEvent<ObjectMapper> event) {
//            final ObjectMapper mapper = event.getBean();
//            mapper.registerModule(new JavaTimeModule());
//            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//            mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
//            return mapper;
//        }
//    }

}