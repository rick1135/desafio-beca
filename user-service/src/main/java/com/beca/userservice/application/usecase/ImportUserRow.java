package com.beca.userservice.application.usecase;

import java.util.List;

public record ImportUserRow(int registro, List<String> columns) {
}
