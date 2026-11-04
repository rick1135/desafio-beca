package com.beca.userservice.application.usecase;

import java.util.List;

public interface ImportUsersUseCase {
    ImportUsersResult importUsers(List<ImportUserRow> rows);
}
