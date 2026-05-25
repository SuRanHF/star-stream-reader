import { http } from './http';
import type { AuthResult, User } from '@/types/api';

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
}

export const authApi = {
  login(usernameOrEmail: string, password: string) {
    return http.post<unknown, AuthResult>('/auth/login', { usernameOrEmail, password });
  },
  register(payload: RegisterPayload) {
    return http.post<unknown, AuthResult>('/auth/register', payload);
  },
  me() {
    return http.get<unknown, { user: User }>('/auth/me');
  },
  createPlayer(playerName: string) {
    return http.post<unknown, unknown>('/auth/create-player', { playerName });
  },
};
