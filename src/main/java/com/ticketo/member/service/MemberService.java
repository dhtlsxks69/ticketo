package com.ticketo.member.service;

import com.ticketo.com.JwtTokenProvider;
import com.ticketo.member.mapper.MemberMapper;
import com.ticketo.member.vo.MemberLoginResponseVO;
import com.ticketo.member.vo.MemberLoginVO;
import com.ticketo.member.vo.MemberUpdateVO;
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
    private final JwtTokenProvider jwtTokenProvider;

    public List<MemberVO> getMemberList() {
        return memberMapper.selectMemberList();
    }

    /** 회원 단건 조회 (비밀번호 제외). 본인 정보만 조회할 수 있고, 없으면 404 */
    public MemberVO getMember(Long id, Long loginMemberId) {
        checkOwner(id, loginMemberId);
        MemberVO member = memberMapper.selectMemberById(id);
        if (member == null) {
            throw notFound(id);
        }
        return member;
    }

    /** 회원 수정: 본인만 이름을 수정할 수 있고, 수정된 회원(비밀번호 제외)을 반환한다. 없으면 404 */
    @Transactional
    public MemberVO updateMember(Long id, Long loginMemberId, MemberUpdateVO request) {
        checkOwner(id, loginMemberId);
        if (memberMapper.updateMemberName(id, request.getName().trim()) == 0) {
            throw notFound(id);
        }
        return memberMapper.selectMemberById(id);
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

    /**
     * 로그인: 이메일 없음/비밀번호 불일치를 구분하지 않고 같은 401 메시지로 응답한다.
     * 성공 시 액세스 토큰과 비밀번호를 제외한 회원 정보를 반환한다.
     */
    public MemberLoginResponseVO login(MemberLoginVO request) {
        String email = request.getEmail().trim().toLowerCase();

        MemberVO member = memberMapper.selectMemberForLoginByEmail(email);
        if (member == null || !passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        String accessToken = jwtTokenProvider.createAccessToken(member.getId());
        return new MemberLoginResponseVO(accessToken, "Bearer", jwtTokenProvider.getAccessTokenExpiresInSeconds(),
                memberMapper.selectMemberById(member.getId()));
    }

    /**
     * 본인 확인. 존재 여부보다 먼저 검사해서, 다른 회원 id가 존재하는지 응답으로 알 수 없게 한다.
     */
    private void checkOwner(Long id, Long loginMemberId) {
        if (!id.equals(loginMemberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 정보만 접근할 수 있습니다.");
        }
    }

    private ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다. id=" + id);
    }
}
