package com.crm.service;

import com.crm.dto.RelayServerForm;
import com.crm.entity.RelayServer;
import com.crm.repository.RelayServerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class RelayServerService {

    private final RelayServerRepository repository;

    public RelayServerService(RelayServerRepository repository) {
        this.repository = repository;
    }

    public List<RelayServer> listAll() {
        return repository.findAllByOrderByNameAsc();
    }

    public Optional<RelayServer> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public RelayServer create(RelayServerForm form) {
        String name = form.getName() == null ? null : form.getName().trim();
        if (name != null && repository.existsByName(name)) {
            throw new DuplicateNameException(name);
        }
        RelayServer r = new RelayServer();
        form.applyTo(r);
        return repository.save(r);
    }

    @Transactional
    public RelayServer update(Long id, RelayServerForm form) {
        RelayServer r = repository.findById(id)
                .orElseThrow(() -> new NotFoundException(id));
        String newName = form.getName() == null ? null : form.getName().trim();
        if (newName != null && !newName.equals(r.getName()) && repository.existsByName(newName)) {
            throw new DuplicateNameException(newName);
        }
        form.applyTo(r);
        return repository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    public static class DuplicateNameException extends RuntimeException {
        public DuplicateNameException(String name) { super("duplicate relay name: " + name); }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(Long id) { super("relay not found: " + id); }
    }
}
