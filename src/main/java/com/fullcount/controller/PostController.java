package com.fullcount.controller;

import com.fullcount.domain.Application;
import com.fullcount.domain.BoardType;
import com.fullcount.dto.PostDto;
import com.fullcount.dto.common.PagedResponse;
import com.fullcount.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Post", description = "게시글 API")
@RestController
@RequestMapping("/api") // /api/posts 뿐만 아니라 /api/applications 도 처리하기 위해 상위 매핑으로 변경
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 목록 조회 (boardType 필터, 페이징)")
    @GetMapping("/posts")
    public ResponseEntity<PagedResponse<PostDto.PostResponse>> getPosts(
            @RequestParam(defaultValue = "GENERAL") BoardType boardType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getPosts(boardType, pageable));
    }

    @Operation(summary = "팀 전용 게시글 목록")
    @GetMapping("/posts/team/{teamId}")
    public ResponseEntity<PagedResponse<PostDto.PostResponse>> getTeamPosts(
            @PathVariable Long teamId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(postService.getTeamPosts(teamId, pageable));
    }

    @Operation(summary = "게시글 상세 조회")
    @GetMapping("/posts/{id}")
    public ResponseEntity<PostDto.PostResponse> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPost(id));
    }

    @Operation(summary = "게시글 작성")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/posts")
    public ResponseEntity<PostDto.PostResponse> createPost(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PostDto.CreatePostRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(memberId, req));
    }

    @Operation(summary = "게시글 수정")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/posts/{id}")
    public ResponseEntity<PostDto.PostResponse> updatePost(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody PostDto.UpdatePostRequest req) {
        return ResponseEntity.ok(postService.updatePost(id, memberId, req));
    }

    @Operation(summary = "게시글 삭제")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(
            @PathVariable Long id,
            @AuthenticationPrincipal Long memberId) {
        postService.deletePost(id, memberId);
        return ResponseEntity.noContent().build();
    }

    // ────── 직관 메이트 신청 API ──────

    @Operation(summary = "직관 메이트 신청하기")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/posts/{postId}/apply")
    public ResponseEntity<Long> applyMeetup(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(postService.applyMeetup(memberId, postId, body.get("message")));
    }

    @Operation(summary = "신청자 목록 조회 (작성자 전용)")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/posts/{postId}/applications")
    public ResponseEntity<List<PostDto.ApplicationResponse>> getApplications(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getApplications(memberId, postId));
    }

    @Operation(summary = "내 신청 정보 조회")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/posts/{postId}/applications/me")
    public ResponseEntity<PostDto.ApplicationResponse> getMyApplication(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long postId) {
        return postService.getMyApplication(postId, memberId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "신청 취소")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/applications/{id}")
    public ResponseEntity<Void> cancelApplication(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        postService.cancelApplication(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "신청 수락")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/applications/{id}/accept")
    public ResponseEntity<Void> acceptApplication(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        postService.acceptApplication(memberId, id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "신청 거절")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/applications/{id}/reject")
    public ResponseEntity<Void> rejectApplication(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long id) {
        postService.rejectApplication(memberId, id);
        return ResponseEntity.noContent().build();
    }
}
