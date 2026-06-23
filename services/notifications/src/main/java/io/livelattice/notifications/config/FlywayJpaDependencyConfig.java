package io.livelattice.notifications.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayJpaDependencyConfig {

    @Bean
    static BeanFactoryPostProcessor flywayJpaDependencyPostProcessor() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
                    return;
                }
                String dependency = beanFactory.containsBeanDefinition("flywayInitializer") ? "flywayInitializer" : "notificationsFlyway";
                if (!beanFactory.containsBeanDefinition(dependency)) {
                    return;
                }
                BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
                Set<String> dependencies = new LinkedHashSet<>();
                String[] existing = definition.getDependsOn();
                if (existing != null) {
                    dependencies.addAll(Set.of(existing));
                }
                dependencies.add(dependency);
                definition.setDependsOn(dependencies.toArray(String[]::new));
            }
        };
    }
}
