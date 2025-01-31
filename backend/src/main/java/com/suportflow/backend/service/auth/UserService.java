package com.suportflow.backend.service.auth;

import com.suportflow.backend.dto.UserDetailsDTO;
import com.suportflow.backend.dto.UserRegistrationDTO;
import com.suportflow.backend.exception.UserNotFoundException;
import com.suportflow.backend.model.Empresa;
import com.suportflow.backend.model.Permissao;
import com.suportflow.backend.model.User;
import com.suportflow.backend.repository.EmpresaRepository;
import com.suportflow.backend.repository.PermissaoRepository;
import com.suportflow.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private PermissaoRepository permissaoRepository;

    @Transactional
    public UserDetailsDTO registerNewUser(UserRegistrationDTO registrationDTO) {
        // 1. Criptografar a senha
        String encodedPassword = passwordEncoder.encode(registrationDTO.getPassword());

        // 2. Buscar a empresa pelo nome (se fornecido)
        Empresa empresa = null;
        if (registrationDTO.getEmpresaNome() != null) {
            empresa = empresaRepository.findByNome(registrationDTO.getEmpresaNome());
            if (empresa == null) {
                // Tratar o caso em que a empresa não existe
                throw new RuntimeException("Empresa não encontrada: " + registrationDTO.getEmpresaNome());
            }
        }

        // 3. Criar o objeto User
        User user = new User();
        user.setNome(registrationDTO.getNome());
        user.setEmail(registrationDTO.getEmail());
        user.setSenha(encodedPassword);
        user.setEmpresa(empresa);
        user.setAtivo(true); // Ou defina como false e implemente um processo de ativação
        user.setDataCriacao(LocalDateTime.now());

        // 4. Salvar o usuário no banco de dados
        user = userRepository.save(user);

        // 5. Retornar o UserDetailsDTO
        return new UserDetailsDTO(user);
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UserNotFoundException("Usuário não encontrado com o email: " + email);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public List<UserDetailsDTO> findAllUsers() {
        return userRepository.findAll().stream()
                .map(UserDetailsDTO::new)
                .collect(Collectors.toList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void adicionarPermissaoSuperAdminSeNecessario(String email, Permissao superAdminPermissao) {
        User adminUser = userRepository.findByEmail(email);
        if (adminUser != null) {
            boolean hasSuperAdmin = adminUser.getPermissoes().stream()
                    .anyMatch(p -> p.getNome().equals("SUPER_ADMIN"));

            if (!hasSuperAdmin) {
                adminUser.getPermissoes().add(superAdminPermissao);
                userRepository.save(adminUser);
            }
        }
    }

    @Transactional
    public UserDetailsDTO updateUser(Long userId, UserRegistrationDTO userDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado com o ID: " + userId));

        // Atualizar os campos do usuário com os valores do DTO, exceto a senha
        user.setNome(userDTO.getNome());
        user.setEmail(userDTO.getEmail());

        // Verifica se uma nova empresa foi fornecida e atualiza a associação
        if (userDTO.getEmpresaNome() != null) {
            Empresa empresa = empresaRepository.findByNome(userDTO.getEmpresaNome());
            if (empresa == null) {
                throw new RuntimeException("Empresa não encontrada: " + userDTO.getEmpresaNome());
            }
            user.setEmpresa(empresa);
        }

        // Salvar as atualizações no banco de dados
        user = userRepository.save(user);

        // Retornar um DTO do usuário atualizado
        return new UserDetailsDTO(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado com o ID: " + userId));

        userRepository.delete(user);
    }

    // Outros métodos, se necessário, podem ser adicionados aqui
}