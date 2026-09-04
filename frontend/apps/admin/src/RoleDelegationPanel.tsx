import { ApiClientError, createApiClient, type AdminUser } from "@pickleball/api-client";
import { useState } from "react";

const api = createApiClient({ baseUrl: import.meta.env.VITE_API_BASE_URL ?? "/api/v1" });
export function RoleDelegationPanel({ token, organizationId }: { token: string; organizationId: string }) {
  const [query, setQuery] = useState("");
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [message, setMessage] = useState("");
  async function search() { try { setUsers(await api.searchAdminUsers(token, query.trim())); setMessage(""); } catch (error) { setMessage(error instanceof ApiClientError ? error.code : "SEARCH_FAILED"); } }
  async function grant(userId: string) { if (!organizationId) return; try { await api.grantCommitteeMember(token, organizationId, userId); setMessage("已授與組織委員會角色，並已留下稽核紀錄。"); } catch (error) { setMessage(error instanceof ApiClientError ? error.code : "GRANT_FAILED"); } }
  async function revoke(userId: string) { if (!organizationId) return; try { await api.revokeCommitteeMember(token, organizationId, userId); setMessage("已撤銷組織委員會角色，並已留下稽核紀錄。"); } catch (error) { setMessage(error instanceof ApiClientError ? error.code : "REVOKE_FAILED"); } }
  return <section aria-label="角色委派"><h2>角色委派</h2><p>僅平台管理員可授與或撤銷目前組織範圍的 COMMITTEE 角色。</p>{!organizationId && <p role="alert">請先在營運總覽選擇組織範圍。</p>}<label>使用者名稱 <input value={query} minLength={2} onChange={(event) => setQuery(event.target.value)} /></label><button disabled={!organizationId || query.trim().length < 2} onClick={() => void search()}>搜尋使用者</button>{message && <p role="status">{message}</p>}<ul>{users.map((user) => <li key={user.id}>{user.displayName} <button onClick={() => void grant(user.id)}>授與委員會</button><button onClick={() => void revoke(user.id)}>撤銷委員會</button></li>)}</ul></section>;
}
