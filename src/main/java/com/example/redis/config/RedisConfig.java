package com.example.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory){
        // 어떤 값을 저장하던 key는 String , value는 json or String
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        // RedisConnectionFactory의 기본값은 Lettuce
        template.setConnectionFactory(connectionFactory);
        // key를 다룰 직렬화 도구
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        RedisSerializer<Object> jsonSerializer = new GenericJackson2JsonRedisSerializer();
        // 3. 일반적인 Key:Value 설정
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        // 4. Hash 자료구조용 Key:Value 설정
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.setDefaultSerializer(jsonSerializer);
        return template;
    }
}
