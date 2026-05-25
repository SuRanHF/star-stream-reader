export interface ApiErrorInfo {
  code?: string;
  message?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiErrorInfo;
}

export interface User {
  id: number;
  username: string;
  email?: string;
  role?: string;
  createdAt?: string;
}

export interface AuthResult extends User {
  token: string;
}

export type ApiRecord = Record<string, unknown>;
