package com.fullcount.service;

import com.fullcount.domain.*;
import com.fullcount.dto.PostDto;
import com.fullcount.dto.common.PagedResponse;
import com.fullcount.exception.BusinessException;
import com.fullcount.exception.ErrorCode;
import com.fullcount.mapper.PostMapper;
import com.fullcount.repository.ApplicationRepository;
import com.fullcount.repository.MemberRepository;
import com.fullcount.repository.PostRepository;
import com.fullcount.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final TeamRepository teamRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public PostDto.PostResponse createPost(Long authorId, PostDto.CreatePostRequest req) {
        Member author = memberRepository.findByIdWithTeam(authorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (req.getBoardType() == BoardType.TRANSFER
                && req.getTicketPrice() != null
                && req.getTicketPrice() < 0) {
            throw new BusinessException(ErrorCode.TICKET_PRICE_EXCEEDED);
        }

        Team homeTeam = req.getHomeTeamId() != null
                ? teamRepository.findById(req.getHomeTeamId()).orElse(null) : null;
        Team awayTeam = req.getAwayTeamId() != null
                ? teamRepository.findById(req.getAwayTeamId()).orElse(null) : null;

        Post post = PostMapper.toEntity(req, author, homeTeam, awayTeam);

        return PostMapper.toResponse(postRepository.save(post));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostDto.PostResponse> getPosts(BoardType boardType, Pageable pageable) {
        Page<PostDto.PostResponse> page = postRepository.findByBoardType(boardType, pageable)
                .map(PostMapper::toResponse);
        return PagedResponse.of(page);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostDto.PostResponse> getTeamPosts(Long teamId, Pageable pageable) {
        Page<PostDto.PostResponse> page = postRepository.findTeamOnlyByTeamId(teamId, pageable)
                .map(PostMapper::toResponse);
        return PagedResponse.of(page);
    }

    @Transactional
    public PostDto.PostResponse getPost(Long postId) {
        Post post = postRepository.findByIdWithAll(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        post.incrementViewCount();
        return PostMapper.toResponse(post);
    }

    @Transactional
    public PostDto.PostResponse updatePost(Long postId, Long memberId, PostDto.UpdatePostRequest req) {
        Post post = findPost(postId);

        if (!post.getAuthor().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        post.updateContent(req.getTitle(), req.getContent());
        return PostMapper.toResponse(post);
    }

    @Transactional
    public void deletePost(Long postId, Long memberId) {
        Post post = findPost(postId);

        if (!post.getAuthor().getId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (!post.isEditable()) {
            throw new BusinessException(ErrorCode.POST_NOT_EDITABLE);
        }

        postRepository.delete(post);
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    // ────── 신청 기능 (Meetup 전용) ──────

    /** 직관 메이트 신청하기 */
    @Transactional
    public Long applyMeetup(Long memberId, Long postId, String message) {
        Post post = findPost(postId);
        Member applicant = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (post.getAuthor().getId().equals(memberId)) {
            throw new IllegalStateException("본인이 작성한 글에는 신청할 수 없습니다.");
        }

        if (applicationRepository.findActiveApplication(postId, memberId).isPresent()) {
            throw new IllegalStateException("이미 신청한 내역이 있습니다.");
        }

        Application application = Application.builder()
                .post(post)
                .applicant(applicant)
                .message(message)
                .build();

        return applicationRepository.save(application).getId();
    }

    /** 신청 수락하기 (작성자 전용) */
    @Transactional
    public void acceptApplication(Long authorId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));

        if (!application.getPost().getAuthor().getId().equals(authorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 신청만 수락할 수 있습니다.");
        }

        application.getPost().addParticipant(); // 인원 수 증가 및 상태 체크
        application.accept();
    }

    /** 신청 거절하기 (작성자 전용) */
    @Transactional
    public void rejectApplication(Long authorId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));

        if (!application.getPost().getAuthor().getId().equals(authorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        application.reject();
    }

    /** 신청 취소하기 (신청자 전용) */
    @Transactional
    public void cancelApplication(Long applicantId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("신청 정보를 찾을 수 없습니다."));

        if (!application.getApplicant().getId().equals(applicantId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        if (application.getStatus() == ApplicationStatus.ACCEPTED) {
            application.getPost().removeParticipant(); // 수락된 인원 감소
        }
        application.cancel();
    }

    /** 내 신청 정보 조회 */
    @Transactional(readOnly = true)
    public Optional<PostDto.ApplicationResponse> getMyApplication(Long postId, Long memberId) {
        return applicationRepository.findActiveApplication(postId, memberId)
                .map(PostMapper::toApplicationResponse);
    }

    /** 신청자 목록 조회 (작성자 전용) */
    @Transactional(readOnly = true)
    public List<PostDto.ApplicationResponse> getApplications(Long authorId, Long postId) {
        Post post = findPost(postId);
        if (!post.getAuthor().getId().equals(authorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return applicationRepository.findByPostIdOrderByCreatedAtDesc(postId).stream()
                .map(PostMapper::toApplicationResponse)
                .toList();
    }
}
