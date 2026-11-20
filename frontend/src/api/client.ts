import axios, { AxiosError } from 'axios';
import type { ProblemDetail } from '../types';

const TOKEN_KEY = 'itsm-token';

export const tokenStore = {
  get: (): string | null => {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  set: (token: string) => {
    try { localStorage.setItem(TOKEN_KEY, token); } catch { /* storage unavailable */ }
  },
  clear: () => {
    try { localStorage.removeItem(TOKEN_KEY); } catch { /* storage unavailable */ }
  },
};

/** Shared HTTP client: adds the JWT and turns 401s into a logout. */
const apiBase = (import.meta.env.VITE_API_URL as string | undefined) || '/api';
export const http = axios.create({ baseURL: apiBase, timeout: 20000 });

http.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let onUnauthorized: (() => void) | null = null;
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

http.interceptors.response.use(
  (res) => res,
  (error: AxiosError) => {
    const url = error.config?.url ?? '';
    if (error.response?.status === 401 && !url.includes('/auth/login')) onUnauthorized?.();
    return Promise.reject(error);
  },
);

/** Extracts a human-readable message from any API error (ProblemDetail aware). */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const problem = error.response?.data as ProblemDetail | undefined;
    if (problem?.fieldErrors && Object.keys(problem.fieldErrors).length > 0) {
      return Object.entries(problem.fieldErrors).map(([f, m]) => `${f}: ${m}`).join(' · ');
    }
    if (problem?.detail) return problem.detail;
    if (error.code === 'ECONNABORTED') return 'The server took too long to respond. Please retry.';
    if (!error.response) return 'Cannot reach the server. Check your connection.';
    return `Request failed (${error.response.status})`;
  }
  return error instanceof Error ? error.message : 'Unexpected error';
}

export function problemOf(error: unknown): ProblemDetail | undefined {
  return axios.isAxiosError(error) ? (error.response?.data as ProblemDetail | undefined) : undefined;
}
