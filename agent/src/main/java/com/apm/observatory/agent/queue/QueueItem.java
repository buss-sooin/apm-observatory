package com.apm.observatory.agent.queue;

// 큐에 담기는 데이터 래퍼
// Record 선택 이유: 큐 안에서 데이터가 변경될 일이 없는 불변 객체
// equals, hashCode, toString 자동 생성
public record QueueItem(DataType type, Object data) {}