package com.example.redis;

import com.example.redis.concert.ConcertQueryService;
import com.example.redis.repository.ConcertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
public class BulkInsert {

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    ConcertQueryService concertService;
    @Autowired
    ConcertRepository concertRepository;

    @BeforeEach
    void initData() {
        // 1. 데이터 개수 확인 (count 쿼리는 빠름)
        long count = concertRepository.count();

        // 2. 이미 100만 건 있으면 패스 (시간 절약)
        if (count > 1_000_000) {
            System.out.println("✅ 이미 데이터가 충분합니다. (현재: " + count + "건)");
            return;
        }

        // 3. 없으면 넣기 (최초 1회만 실행됨)
        System.out.println("🚀 데이터가 부족합니다. 벌크 인서트 시작...");
        bulkInsert();
    }

    @Test
    @DisplayName("벌크 데이터 삽입 100만건")
    void bulkInsert() {
        // 1. 사이즈 및 배치 크기 설정
        final int TOTAL_COUNT = 1_000_000; // 100만 건
        final int BATCH_SIZE = 1000;       // 1000개씩 묶어서 DB로 전송

        System.out.println("데이터 삽입 시작...");
        long startTime = System.currentTimeMillis();

        List<Object[]> batchArgs = new ArrayList<>();

        // 무거운 더미 데이터 (디스크 I/O 유발용)
        String heavyTrashData = "A".repeat(100);

        for (int i = 1; i <= TOTAL_COUNT; i++) {
            // DB 컬럼 순서대로 값 세팅 (concert_id는 Auto Increment라 제외)
            // SQL: INSERT INTO concert (concert_name, concert_day, seats, dummy_data) VALUES (?, ?, ?, ?)
            batchArgs.add(new Object[]{
                    "Concert-" + i,        // concert_name
                    LocalDate.now(),       // concert_day
                    100,                   // seats
                    heavyTrashData         // dummy_data (없으면 제거)
            });

            // 1000개 찰 때마다 DB로 발사
            if (i % BATCH_SIZE == 0) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO concert (concert_name, concert_day, seats, dummy_description) VALUES (?, ?, ?, ?)",
                        batchArgs
                );
                batchArgs.clear(); // 메모리 비우기
                System.out.println(i + "건 저장 완료"); // 진행상황 로그
            }
        }

        // 혹시 남은 짜투리 저장
        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO concert (concert_name, concert_day, seats, dummy_description) VALUES (?, ?, ?, ?)",
                    batchArgs
            );
        }

        long endTime = System.currentTimeMillis();
        System.out.println("총 걸린 시간: " + (endTime - startTime) + "ms");
    }


    @Test
    @DisplayName("풀스캔 vs 인덱스 vs 레디스")
    void comparePerformance() {
        // 검색 대상: 삽입 데이터 중 가장 마지막에 있는 것
        String targetName = "Concert-999999";

        // 일반 DB 조회
        long startDB = System.currentTimeMillis();

        // 캐시 안 타는 메서드 or 캐시 강제 삭제 후 호출
        concertService.findNoCache(targetName);

        long endDB = System.currentTimeMillis();
        System.out.println("1️⃣ DB 조회 소요 시간: " + (endDB - startDB) + "ms");


        // -------------------------------------------------------
        // 시나리오 2: Redis 캐시 적용 (첫 조회 - Cache Miss)
        // -------------------------------------------------------
        // DB에서 가져와서 -> Redis에 적재하는 비용까지 포함됨
        evictCache(targetName); // 캐시 비우기 (공정한 측정을 위해)

        long startMiss = System.currentTimeMillis();
        concertService.findWithCache(targetName);
        long endMiss = System.currentTimeMillis();
        System.out.println("2️⃣ 캐시 적용(첫 조회/Miss) 소요 시간: " + (endMiss - startMiss) + "ms");


        // -------------------------------------------------------
        // 시나리오 3: Redis 캐시 적용 (재 조회 - Cache Hit)
        // -------------------------------------------------------
        // 이미 메모리에 올라간 상태 -> DB 안 감
        long startHit = System.currentTimeMillis();
        concertService.findWithCache(targetName);
        long endHit = System.currentTimeMillis();
        System.out.println("3️⃣ 캐시 적용(재 조회/Hit) 소요 시간: " + (endHit - startHit) + "ms");
    }

    @Autowired
    RedisTemplate<String, String> redisTemplate;
    private void evictCache(String name) {
        redisTemplate.delete("concerts::" + name);
    }
}