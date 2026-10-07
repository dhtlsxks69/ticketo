package com.ticketo.member.controller;

import com.ticketo.member.service.MemberService;
import com.ticketo.member.vo.MemberVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** 회원 목록 조회 */
    @GetMapping
    public List<MemberVO> list() {
        return memberService.getMemberList();
    }

    /** 회원가입 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberVO register(@Valid @RequestBody MemberVO member) {
        return memberService.register(member);
    }
}
