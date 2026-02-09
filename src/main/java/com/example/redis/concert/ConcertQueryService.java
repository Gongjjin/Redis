package com.example.redis.concert;

import com.example.redis.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConcertQueryService {
    private final ConcertRepository concertRepository;

    /**
     * 풀스캔 서치
     * @param name
     * @return
     */
    public List<ConcertResponse> findNoCache(String name) {
        List<Concert> concerts = concertRepository.findByConcertName(name);
        return concerts.stream().map(ConcertResponse::from).toList();
    }

    /**
     * Redis 서치
     * 값이 이렇게 생김 (name::concerts)
     * @param name
     * @return
     */
    @Cacheable(value = "concerts", key = "#name")
    public List<ConcertResponse> findWithCache(String name) {
        List<Concert> concerts = concertRepository.findByConcertName(name);
        return concerts.stream().map(ConcertResponse::from).toList();
    }
}
