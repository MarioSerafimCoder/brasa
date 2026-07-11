export class AppError extends Error { constructor(message, { code = "INTERNAL_ERROR", status = 500, details } = {}) { super(message); this.name = this.constructor.name; this.code = code; this.status = status; this.publicMessage = message; this.details = details; } }
export class ValidationError extends AppError { constructor(message = "Dados inválidos.", details) { super(message, { code: "VALIDATION_ERROR", status: 400, details }); } }
export class NotFoundError extends AppError { constructor(message = "Recurso não encontrado.", details) { super(message, { code: "NOT_FOUND", status: 404, details }); } }
export class ConflictError extends AppError { constructor(message = "A operação está em conflito.", details) { super(message, { code: "CONFLICT", status: 409, details }); } }
export class UnauthorizedError extends AppError { constructor(message = "Autenticação necessária.") { super(message, { code: "UNAUTHORIZED", status: 401 }); } }
export class ForbiddenError extends AppError { constructor(message = "Ação não permitida.") { super(message, { code: "FORBIDDEN", status: 403 }); } }
export class PayloadTooLargeError extends AppError { constructor() { super("O conteúdo enviado excede o limite permitido.", { code: "PAYLOAD_TOO_LARGE", status: 413 }); } }
