# [Session 06] AI 활용 프롬프트 -- 고가용성

## 1단계: 이해

```
Active-Active vs Active-Passive 페일오버의 차이를 설명해줘.
각각의 장단점과 적합한 상황을 알려줘.
```

```
Rolling, Blue-Green, Canary 배포 전략 3가지를 비교해줘.
각 전략의 장단점과 롤백 용이성을 표로 정리해줘.
```

## 2단계: 적용

```
Nginx 헬스체크 설정을 만들어줘.
5초마다 /actuator/health를 확인하고,
3번 연속 실패하면 해당 서버를 제외하는 설정이야.
```

## 3단계: 검증

```
로드밸런서 자체가 SPOF(Single Point of Failure)가 되는 것은
어떻게 방지해? Keepalived + VIP 구성을 설명해줘.
```

```
[AI가 틀릴 수 있는 포인트]
AI가 제안하는 HA 아키텍처는 이론적으로는 맞지만,
실제 구축 비용과 복잡성을 고려하지 않을 수 있습니다.
```
