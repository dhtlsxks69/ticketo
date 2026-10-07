package com.ticketo.member.service;

import com.ticketo.member.mapper.MemberMapper;
import com.ticketo.member.vo.MemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public List<MemberVO> getMemberList() {
        return memberMapper.selectMemberList();
    }

    /** 회원가입: 이메일 중복 검사 후 비밀번호를 BCrypt로 해시해 저장하고, 저장된 회원(비밀번호 제외)을 반환한다. */
    @Transactional
    public MemberVO register(MemberVO member) {
        member.setEmail(member.getEmail().trim().toLowerCase());

        if (memberMapper.existsByEmail(member.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        member.setPassword(passwordEncoder.encode(member.getPassword()));
        try {
            memberMapper.insertMember(member);
        } catch (DuplicateKeyException e) {
            // 중복 검사와 INSERT 사이에 동일 이메일이 가입된 경우(동시 요청)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        }
        return memberMapper.selectMemberById(member.getId());
    }
}
