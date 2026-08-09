package com.jobtracker.service;

import com.jobtracker.Utils.JobUtils;
import com.jobtracker.dto.InterviewRoundRequestDto;
import com.jobtracker.dto.InterviewRoundResponseDto;
import com.jobtracker.dto.UpcomingRoundResponseDto;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.model.InterviewRound;
import com.jobtracker.model.JobApplication;
import com.jobtracker.model.User;
import com.jobtracker.repository.InterviewRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewRoundService {

    private final InterviewRoundRepository interviewRoundRepository;
    private final JobApplicationService jobApplicationService;
    private final JobUtils jobUtils;

    public List<InterviewRoundResponseDto> listRounds(User user, Integer jobId) {
        // Reuses the job ownership check — if the caller doesn't own the job, this 404s
        // before any round is ever read.
        jobApplicationService.findOwnedJob(user, jobId);
        return interviewRoundRepository.findByJobApplication_JobIdOrderByRoundNumberAsc(jobId)
                .stream()
                .map(jobUtils::toRoundResponseDto)
                .toList();
    }

    /**
     * Cross-job read, so there is no findOwnedJob call to lean on — the repository query is
     * itself scoped by user id, which is what keeps another user's rounds out of the result.
     */
    public List<UpcomingRoundResponseDto> listUpcomingRounds(User user) {
        return interviewRoundRepository.findUpcomingByUser(user.getUserId(), LocalDateTime.now())
                .stream()
                .map(jobUtils::toUpcomingRoundDto)
                .toList();
    }

    public InterviewRoundResponseDto getRound(User user, Integer jobId, Integer roundId) {
        jobApplicationService.findOwnedJob(user, jobId);
        return jobUtils.toRoundResponseDto(findRound(jobId, roundId));
    }

    public InterviewRoundResponseDto createRound(User user, Integer jobId, InterviewRoundRequestDto dto) {
        JobApplication job = jobApplicationService.findOwnedJob(user, jobId);
        InterviewRound round = new InterviewRound();
        round.setJobApplication(job);
        jobUtils.applyToEntity(dto, round);
        return jobUtils.toRoundResponseDto(interviewRoundRepository.save(round));
    }

    public InterviewRoundResponseDto updateRound(User user, Integer jobId, Integer roundId, InterviewRoundRequestDto dto) {
        jobApplicationService.findOwnedJob(user, jobId);
        InterviewRound round = findRound(jobId, roundId);
        jobUtils.applyToEntity(dto, round);
        return jobUtils.toRoundResponseDto(interviewRoundRepository.save(round));
    }

    @Transactional
    public void deleteRound(User user, Integer jobId, Integer roundId) {
        jobApplicationService.findOwnedJob(user, jobId);
        interviewRoundRepository.delete(findRound(jobId, roundId));
    }

    private InterviewRound findRound(Integer jobId, Integer roundId) {
        return interviewRoundRepository.findByJobRoundIdAndJobApplication_JobId(roundId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview round not found"));
    }
}
