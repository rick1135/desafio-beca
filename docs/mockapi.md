# API Mock de saldo e limites

A API externa é simulada no [MockAPI](https://mockapi.io/). O recurso `accounts` contém uma conta por usuário do MS1.

## Configuração

1. Crie um projeto no MockAPI.
2. Crie o recurso `accounts` com os campos abaixo.
3. Cadastre manualmente as contas de exemplo.
4. Copie a URL base do projeto para `MOCK_API_BASE_URL` no arquivo `.env` local.

| Campo | Tipo no MockAPI | Descrição |
|---|---|---|
| `id` | Gerado automaticamente | ID do registro no MockAPI; pode ser retornado como texto |
| `userId` | Number | ID correspondente ao usuário no MS1 |
| `balance` | Number | Saldo de referência em BRL |
| `dailyLimit` | Number | Valor máximo de saídas aprovadas por dia |
| `transactionLimit` | Number | Valor máximo de uma saída |
| `currency` | String | `BRL` |

Mantenha exatamente uma conta para cada `userId`. O `id` do MockAPI não precisa ser igual ao `userId`.

## Contrato de consulta

```http
GET {MOCK_API_BASE_URL}/accounts?userId={userId}
```

Exemplo de resposta para `userId=1`:

```json
[
  {
    "id": "1",
    "userId": 1,
    "balance": 1000,
    "dailyLimit": 800,
    "transactionLimit": 500,
    "currency": "BRL"
  }
]
```

A resposta é uma lista. Na integração, o MS2 deverá confirmar que existe exatamente um registro cujo `userId` corresponde ao usuário consultado. Uma lista vazia ou com contas duplicadas não fornece uma conta válida.

## Contas para demonstração

Cadastre os usuários correspondentes no MS1 antes de usar estes exemplos.

| `userId` | `balance` | `dailyLimit` | `transactionLimit` | Demonstração |
|---|---:|---:|---:|---|
| 1 | 1000 | 800 | 500 | Saída de 100 dentro dos limites |
| 2 | 50 | 800 | 500 | Saída de 100 acima do saldo |
| 3 | 2000 | 1000 | 100 | Compra de 150 acima do limite por transação |
| 4 | 2000 | 600 | 500 | Duas saídas de 400 no mesmo dia excedem o limite diário na segunda operação |

Defina `currency` como `BRL` em todos os registros. Os resultados acima serão demonstrados quando as regras de aprovação estiverem implementadas no MS2.

## Regras previstas para o MS2

Antes de aprovar retirada, transferência enviada ou compra, consultar a conta do usuário e verificar:

- Saldo suficiente para o valor da transação.
- Valor até `transactionLimit`.
- Total de saídas aprovadas no dia, incluindo a nova transação, até `dailyLimit`.

Depósitos não exigem consulta à API Mock. Se a API estiver indisponível, a saída não pode ser aprovada. O MS2 deverá registrar a rejeição com um motivo claro, conforme definido no épico de saldo e limites.

A DB-27 define `balance` como saldo inicial fixo e calcula o saldo efetivo com as transações aprovadas no MS2. O Pedido do Product Owner também pede que a rota de saldo reflita o valor presente no MockAPI naquele momento. Confirmar com o PO como a rota deve apresentar esses dois valores antes de implementar essa parte.

## Verificação da DB-26

No Postman/Insomnia, consulte `GET {MOCK_API_BASE_URL}/accounts?userId=1` e confira os campos e valores. Repita para as quatro contas. Confirme que cada consulta identifica somente a conta do usuário esperado.