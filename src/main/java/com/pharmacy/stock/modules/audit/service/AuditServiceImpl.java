package com.pharmacy.stock.modules.audit.service;

import com.pharmacy.stock.modules.audit.entity.ActionLog;
import com.pharmacy.stock.modules.audit.repository.ActionLogRepository;
import com.pharmacy.stock.modules.user.entity.User;
import com.pharmacy.stock.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final ActionLogRepository actionLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String actionType, String entityAffected, Long recordId, String details, String ipAddress) {
        userRepository.findById(userId).ifPresentOrElse(user -> {
            ActionLog actionLog = new ActionLog();
            actionLog.setUser(user);
            actionLog.setActionType(actionType);
            actionLog.setEntityAffected(entityAffected);
            actionLog.setRecordId(recordId != null ? recordId.intValue() : 0);
            actionLog.setDetails(details);
            actionLog.setIpAddress(ipAddress);
            actionLogRepository.save(actionLog);
        }, () -> log.warn("Journalisation impossible : utilisateur introuvable id={}", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionLog> search(Long userId, String actionType, String entityAffected) {
        return actionLogRepository.search(userId, actionType, entityAffected);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> actionTypes() {
        return actionLogRepository.findDistinctActionTypes();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> entitiesAffected() {
        return actionLogRepository.findDistinctEntitiesAffected();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionLog> findRecent(int limit) {
        return actionLogRepository.search(null, null, null).stream().limit(limit).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return actionLogRepository.count();
    }
}
