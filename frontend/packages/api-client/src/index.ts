import {
  AuthenticationApi,
  Configuration,
  CurrentUserApi,
  ResponseError,
  type LoginData,
  type Me,
  type ProfileUpdateRequest,
  type RoleCode,
  type RoleContext,
} from "./generated/src";

export type { LoginData as Login, Me, ProfileUpdateRequest as ProfileUpdate, RoleCode, RoleContext };
export type ApiClientOptions = { baseUrl: string };

export class ApiClientError extends Error {
  constructor(public readonly status: number, public readonly code: string) { super(code); }
}

async function mapError(caught: unknown): Promise<never> {
  if (caught instanceof ResponseError) {
    const body = await caught.response.clone().json().catch(() => null) as { error?: { code?: string } } | null;
    throw new ApiClientError(caught.response.status, body?.error?.code ?? "REQUEST_FAILED");
  }
  throw caught;
}

/** Handwritten adapter over the OpenAPI-generated client. Apps never construct URLs themselves. */
export function createApiClient({ baseUrl }: ApiClientOptions) {
  const anonymous = new AuthenticationApi(new Configuration({ basePath: baseUrl }));
  const authenticated = (token: string) => new CurrentUserApi(new Configuration({ basePath: baseUrl, accessToken: token }));
  return {
    baseUrl,
    async loginWithLine(idToken: string): Promise<LoginData> { try { return (await anonymous.loginWithLine({ lineLoginRequest: { idToken } })).data; } catch (caught) { return mapError(caught); } },
    async me(token: string): Promise<Me> { try { return (await authenticated(token).getCurrentUser()).data; } catch (caught) { return mapError(caught); } },
    async roles(token: string): Promise<RoleContext[]> { try { return (await authenticated(token).getCurrentUserRoles()).data; } catch (caught) { return mapError(caught); } },
    async updateProfile(token: string, profile: ProfileUpdateRequest): Promise<Me> { try { return (await authenticated(token).updateCurrentUserProfile({ profileUpdateRequest: profile })).data; } catch (caught) { return mapError(caught); } },
  };
}
