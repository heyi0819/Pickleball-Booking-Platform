export type ApiClientOptions = {
  baseUrl: string;
};

export function createApiClient({ baseUrl }: ApiClientOptions) {
  return { baseUrl };
}
