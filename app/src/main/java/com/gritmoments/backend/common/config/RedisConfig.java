package com.gritmoments.backend.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 설정 (세션 01: 캐시, 세션 05: 분산 락)
 *
 * - RedisTemplate: Redis에 직접 명령어를 실행할 때 사용 (cache-aside 패턴, 분산 락)
 * - CacheManager: @Cacheable 어노테이션으로 자동 캐싱할 때 사용
 */
@Configuration
public class RedisConfig {

    /**
     * Java 8 날짜/시간 지원 + 타입 정보가 포함된 JSON 직렬화기
     */
    private GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    /**
     * RedisTemplate 설정
     * - Key는 String, Value는 JSON으로 직렬화
     * - 세션 01 L3(cache-aside 직접 구현)에서 사용
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key: 문자열로 저장 (가독성)
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value: JSON으로 저장 (디버깅 편의)
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    /**
     * 캐시 매니저 설정
     * - @Cacheable, @CacheEvict 등에서 사용
     * - 기본 TTL: 5분
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 기본 TTL 5분 (세션 01 L2에서 변경 실험)
                .entryTtl(Duration.ofMinutes(5))
                // null 값은 캐싱하지 않음
                .disableCachingNullValues()
                // Key 접두사 설정 (캐시 이름::키)
                .prefixCacheNameWith("grit::")
                // Value를 JSON으로 저장 (JavaTimeModule 포함)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonRedisSerializer())
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                // 특정 캐시에 개별 TTL 설정
                .withCacheConfiguration("menus",
                        config.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("restaurants",
                        config.entryTtl(Duration.ofMinutes(30)))
                .build();
    }
}
