package com.ticketo.member.controller;

import com.ticketo.member.service.MemberService;
import com.ticketo.member.vo.MemberLoginResponseVO;
import com.ticketo.member.vo.MemberLoginVO;
import com.ticketo.member.vo.MemberUpdateVO;
import com.ticketo.member.vo.MemberVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** 회원 단건 조회 (로그인 필요, 본인만) */
    @GetMapping("/{id}")
    public MemberVO get(@PathVariable Long id, @AuthenticationPrincipal Long loginMemberId) {
        return memberService.getMember(id, loginMemberId);
    }

    /** 회원가입 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberVO register(@Valid @RequestBody MemberVO member) {
        return memberService.register(member);
    }

    /** 회원 수정 (로그인 필요, 본인만, 이름만 수정 가능) */
    @PutMapping("/{id}")
    public MemberVO update(@PathVariable Long id, @AuthenticationPrincipal Long loginMemberId,
            @Valid @RequestBody MemberUpdateVO request) {
        return memberService.updateMember(id, loginMemberId, request);
    }

    /** 로그인 (이메일/비밀번호 확인). 성공 시 액세스 토큰과 비밀번호를 제외한 회원 정보를 반환한다. */
    @PostMapping("/login")
    public MemberLoginResponseVO login(@Valid @RequestBody MemberLoginVO request) {
        return memberService.login(request);
    }
}
