package com.swkitchen.member.domain;

// 쿠키에 목적을 담아, 찾기로 받은 본인확인으로 가입(만 14세 검사 없이)하지 못하게 한다
public enum VerificationPurpose {
    SIGNUP, FIND, PHONE_CHANGE
}
