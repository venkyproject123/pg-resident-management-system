import { useEffect, useRef, useState } from "react";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
const apiUrl = (path) => `${API_BASE_URL}${path}`;
const RESIDENTS_API = "/api/residents";
const formatCurrency = (amount) => new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2
}).format(amount || 0);
const emptyForm = {
  name: "",
  fatherName: "",
  mobileNumber: "",
  roomNumber: "",
  sharing: "",
  rent: "",
  advanceAmount: "",
  joiningDate: "",
  permanentAddress: "",
  companyAddress: ""
};

function encodeBasicCredentials(credentials) {
  const bytes = new TextEncoder().encode(`${credentials.username}:${credentials.password}`);
  let binary = "";
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary);
}

function SecretInput(props) {
  const [visible, setVisible] = useState(false);
  return (
    <span className="secret-input-wrap">
      <input {...props} type={visible ? "text" : "password"} />
      <button
        type="button"
        className="secret-visibility-toggle"
        aria-label={visible ? "Hide value" : "Show value"}
        aria-pressed={visible}
        onClick={() => setVisible(!visible)}
      >
        {visible ? (
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.2A11 11 0 0 1 12 5c5 0 8.5 4 9.5 7-.4 1.1-1.2 2.3-2.3 3.3M6.2 6.2C4.2 7.5 2.9 9.3 2.5 12c.6 1.8 3.8 7 9.5 7 1.1 0 2.1-.2 3-.5" /></svg>
        ) : (
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7Z" /><circle cx="12" cy="12" r="2.5" /></svg>
        )}
      </button>
    </span>
  );
}

async function request(
  path,
  credentials,
  options = {},
  unauthorizedMessage = "Login failed. Check the owner username and password."
) {
  const response = await fetch(apiUrl(path), {
    ...options,
    headers: {
      Authorization: `Basic ${encodeBasicCredentials(credentials)}`,
    ...(options.body && !(options.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
      ...options.headers
    }
  });

  if (!response.ok) {
    let detail = "";
    try {
      const body = await response.json();
      detail = body.detail || body.message || body.error || "";
    } catch {
      detail = "";
    }
    if (response.status === 401) {
      const authenticationError = new Error(unauthorizedMessage);
      authenticationError.status = response.status;
      throw authenticationError;
    }
    if (response.status === 404) {
      const notFoundError = new Error("That resident could not be found.");
      notFoundError.status = response.status;
      throw notFoundError;
    }
    const requestError = new Error(detail || `Request failed (${response.status}). Check the submitted values and try again.`);
    requestError.status = response.status;
    throw requestError;
  }

  if (response.status === 204) return null;
  return response.json();
}

async function publicRequest(path, body) {
  const response = await fetch(apiUrl(path), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!response.ok) {
    let detail = "";
    try {
      const result = await response.json();
      detail = result.detail || result.message || "";
    } catch {
      detail = "";
    }
    throw new Error(detail || `Request failed (${response.status}).`);
  }
  return response.status === 204 || response.status === 202 || response.status === 201
    ? null
    : response.json();
}

function residentToForm(resident) {
  return {
    name: resident.name,
    fatherName: resident.fatherName,
    mobileNumber: resident.mobileNumber?.replace(/^\+91/, "") || "",
    roomNumber: resident.roomNumber,
    sharing: resident.sharing ? String(resident.sharing) : "",
    rent: String(resident.rent),
    advanceAmount: String(resident.advanceAmount),
    joiningDate: resident.joiningDate,
    permanentAddress: resident.permanentAddress,
    companyAddress: resident.companyAddress
  };
}

function sharingLabel(sharing) {
  return `${sharing} sharing`;
}

function getCurrentPaymentPeriod() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function readSavedSession() {
  try {
    const value = sessionStorage.getItem("haven-auth-session");
    if (!value) return null;
    const session = JSON.parse(value);
    if ((session.type === "owner" || session.type === "admin")
      && typeof session.credentials?.username === "string"
      && typeof session.credentials?.password === "string") {
      return session;
    }
  } catch {
    return null;
  }
  return null;
}

function App() {
  const [selectedPaymentPeriod, setSelectedPaymentPeriod] = useState(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  });
  const currentMonthName = new Date()
    .toLocaleString(undefined, { month: "long" });
  const [savedSession] = useState(readSavedSession);
  const [credentials, setCredentials] = useState(
    savedSession?.type === "owner" ? savedSession.credentials : null
  );
  const [loginForm, setLoginForm] = useState({ username: "", password: "" });
  const [adminCredentials, setAdminCredentials] = useState(
    savedSession?.type === "admin" ? savedSession.credentials : null
  );
  const [adminLoginForm, setAdminLoginForm] = useState({ username: "", password: "" });
  const [pendingOwners, setPendingOwners] = useState([]);
  const [approvedOwners, setApprovedOwners] = useState([]);
  const [authMode, setAuthMode] = useState("LANDING");
  const [setupForm, setSetupForm] = useState({ username: "", password: "", pgName: "", location: "", mobileNumber: "" });
  const [registrationSuccessPgName, setRegistrationSuccessPgName] = useState("");
  const [registrationCredentials, setRegistrationCredentials] = useState(null);
  const [registrationApproved, setRegistrationApproved] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileForm, setProfileForm] = useState({ username: "", pgName: "", location: "" });
  const [residentPage, setResidentPage] = useState({
    content: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true
  });
  const [residentPageIndex, setResidentPageIndex] = useState(0);
  const [dashboardSummary, setDashboardSummary] = useState(null);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [residentLoading, setResidentLoading] = useState(false);
  const [monthlyPaymentsLoading, setMonthlyPaymentsLoading] = useState(false);
  const [rooms, setRooms] = useState([]);
  const [roomForm, setRoomForm] = useState({ roomNumber: "", sharing: "" });
  const [monthlyPaymentReport, setMonthlyPaymentReport] = useState(null);
  const [monthlyPaymentsOpen, setMonthlyPaymentsOpen] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [roomSearchText, setRoomSearchText] = useState("");
  const [emptyBedsOpen, setEmptyBedsOpen] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState("ACTIVE");
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);
  const [authRestoring, setAuthRestoring] = useState(Boolean(savedSession));
  const [photoFile, setPhotoFile] = useState(null);
  const [identityProofFile, setIdentityProofFile] = useState(null);
  const [documentPreview, setDocumentPreview] = useState(null);
  const [documentError, setDocumentError] = useState("");
  const [viewerResident, setViewerResident] = useState(null);
  const [rentPaymentResident, setRentPaymentResident] = useState(null);
  const [rentPaymentYear, setRentPaymentYear] = useState(new Date().getFullYear());
  const [rentPayments, setRentPayments] = useState([]);
  const [paymentDraft, setPaymentDraft] = useState(null);
  const photoInput = useRef(null);
  const identityProofInput = useRef(null);

  useEffect(() => {
    if (!credentials) return undefined;

    const keepDashboardOpen = () => {
      setMonthlyPaymentsOpen(false);
      setEmptyBedsOpen(false);
      setFormOpen(false);
      setForm(emptyForm);
      setEditingId(null);
      setPhotoFile(null);
      setIdentityProofFile(null);
      setRentPaymentResident(null);
      setPaymentDraft(null);
      setViewerResident(null);
      setDocumentPreview(null);
      setDocumentError("");
      setError("");
      window.history.pushState({ havenDashboard: true }, "", window.location.href);
    };

    window.history.pushState({ havenDashboard: true }, "", window.location.href);
    window.history.pushState({ havenDashboard: true }, "", window.location.href);
    window.addEventListener("popstate", keepDashboardOpen);
    return () => window.removeEventListener("popstate", keepDashboardOpen);
  }, [credentials]);

  useEffect(() => {
    return () => {
      if (documentPreview?.url) URL.revokeObjectURL(documentPreview.url);
    };
  }, [documentPreview]);

  useEffect(() => {
    if (!notice) return undefined;
    const timeoutId = window.setTimeout(() => setNotice(""), 3000);
    return () => window.clearTimeout(timeoutId);
  }, [notice]);

  const residents = residentPage.content;

  useEffect(() => {
    if (savedSession) {
      let mounted = true;
      setLoading(true);
      const restoreSession = async () => {
        try {
          if (savedSession.type === "owner") {
            const profile = await request("/api/owner/profile", savedSession.credentials);
            if (!mounted) return;
            setProfileForm({
              username: profile.username,
              pgName: profile.pgName,
              location: profile.location || ""
            });
          } else {
            const overview = await request(
              "/api/admin/owners",
              savedSession.credentials,
              {},
              "Admin authentication failed. Please sign in again."
            );
            if (!mounted) return;
            setPendingOwners(overview.pending);
            setApprovedOwners(overview.approved);
          }
        } catch (requestError) {
          if (requestError.status === 401 || requestError.status === 403) {
            sessionStorage.removeItem("haven-auth-session");
          }
          if (mounted) {
            setCredentials(null);
            setAdminCredentials(null);
            setError(requestError.status === 401 || requestError.status === 403
              ? "Your session expired. Please sign in again."
              : `Could not restore your session. Check your connection and sign in again. ${requestError.message}`);
          }
        } finally {
          if (mounted) {
            setLoading(false);
            setAuthRestoring(false);
          }
        }
      };
      restoreSession();
      return () => { mounted = false; };
    }

    return undefined;
  }, []);

  useEffect(() => {
    if (!credentials) return undefined;
    let active = true;
    const [year, month] = getCurrentPaymentPeriod().split("-").map(Number);
    setDashboardLoading(true);
    request(`${RESIDENTS_API}/dashboard?year=${year}&month=${month}`, credentials)
      .then((summary) => {
        if (!active) return;
        setDashboardSummary(summary);
        setRooms(summary.rooms);
      }).catch((requestError) => {
        if (active) setError(requestError.message);
      }).finally(() => {
        if (active) setDashboardLoading(false);
      });
    return () => { active = false; };
  }, [credentials]);

  useEffect(() => {
    if (!credentials) return undefined;
    let active = true;
    setResidentLoading(true);
    const timeoutId = window.setTimeout(async () => {
      const params = new URLSearchParams({
        page: String(residentPageIndex),
        size: "20",
        status: selectedStatus
      });
      if (searchText.trim()) params.set("keyword", searchText.trim());
      try {
        const page = await request(`${RESIDENTS_API}/page?${params}`, credentials);
        if (!active) return;
        if (page.content.length === 0 && page.page > 0 && page.page >= page.totalPages) {
          setResidentPageIndex(page.page - 1);
          return;
        }
        setResidentPage(page);
      } catch (requestError) {
        if (active) setError(requestError.message);
      } finally {
        if (active) setResidentLoading(false);
      }
    }, searchText.trim() ? 300 : 0);
    return () => {
      active = false;
      window.clearTimeout(timeoutId);
    };
  }, [credentials, residentPageIndex, searchText, selectedStatus]);

  useEffect(() => {
    if (!credentials || !monthlyPaymentsOpen) return undefined;
    let active = true;
    const [year, month] = selectedPaymentPeriod.split("-").map(Number);
    setMonthlyPaymentsLoading(true);
    setMonthlyPaymentReport(null);
    request(`${RESIDENTS_API}/monthly-payments?year=${year}&month=${month}`, credentials)
      .then((report) => {
        if (active) setMonthlyPaymentReport(report);
      })
      .catch((requestError) => {
        if (active) setError(requestError.message);
      })
      .finally(() => {
        if (active) setMonthlyPaymentsLoading(false);
      });
    return () => { active = false; };
  }, [credentials, monthlyPaymentsOpen, selectedPaymentPeriod]);

  useEffect(() => {
    if (authMode !== "REGISTRATION_SUCCESS" || !registrationCredentials || registrationApproved) return undefined;

    let stopped = false;
    let timeoutId;
    const checkApproval = async () => {
      try {
        const response = await fetch(apiUrl("/api/owner/profile"), {
          headers: {
            Authorization: `Basic ${encodeBasicCredentials(registrationCredentials)}`
          },
          cache: "no-store"
        });
        if (stopped) return;
        if (response.ok) {
          setRegistrationApproved(true);
          setError("");
          setLoginForm({ username: registrationCredentials.username, password: "" });
          setRegistrationCredentials(null);
          return;
        }
        if (response.status === 401) {
          setError("");
        } else {
          setError(`Could not check approval status (${response.status}). We’ll try again shortly.`);
        }
      } catch {
        if (!stopped) setError("Could not check approval status. Check your connection; we’ll try again shortly.");
      }
      if (!stopped) timeoutId = window.setTimeout(checkApproval, 5000);
    };

    checkApproval();
    return () => {
      stopped = true;
      window.clearTimeout(timeoutId);
    };
  }, [authMode, registrationCredentials, registrationApproved]);

  async function loadResidents(owner, page = residentPageIndex, keyword = searchText, status = selectedStatus) {
    const params = new URLSearchParams({
      page: String(page),
      size: "20",
      status
    });
    if (keyword.trim()) params.set("keyword", keyword.trim());
    const result = await request(`${RESIDENTS_API}/page?${params}`, owner);
    setResidentPage(result);
    setResidentPageIndex(
      result.content.length === 0 && result.page > 0 && result.page >= result.totalPages
        ? result.page - 1
        : result.page
    );
    return result;
  }

  async function loadDashboard(owner, period = getCurrentPaymentPeriod()) {
    const [year, month] = period.split("-").map(Number);
    const summary = await request(`${RESIDENTS_API}/dashboard?year=${year}&month=${month}`, owner);
    setDashboardSummary(summary);
    setRooms(summary.rooms);
  }

  async function loadMonthlyPayments(owner, period = selectedPaymentPeriod) {
    const [year, month] = period.split("-").map(Number);
    const report = await request(`${RESIDENTS_API}/monthly-payments?year=${year}&month=${month}`, owner);
    setMonthlyPaymentReport(report);
  }

  async function refreshOwnerData(owner) {
    await Promise.all([
      loadDashboard(owner),
      loadResidents(owner),
      monthlyPaymentsOpen ? loadMonthlyPayments(owner) : Promise.resolve()
    ]);
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const profile = await request("/api/owner/profile", loginForm);
      sessionStorage.setItem("haven-auth-session", JSON.stringify({ type: "owner", credentials: loginForm }));
      setProfileForm({
        username: profile.username,
        pgName: profile.pgName,
        location: profile.location || ""
      });
      setCredentials(loginForm);
      setNotice("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleSetupOwner(event) {
    event.preventDefault();
    if (setupForm.password.length < 12) {
      setError("You need to enter a password with at least 12 characters.");
      return;
    }
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await publicRequest("/api/owner/setup", setupForm);
      setRegistrationSuccessPgName(setupForm.pgName.trim());
      setRegistrationCredentials({ username: setupForm.username, password: setupForm.password });
      setRegistrationApproved(false);
      setSetupForm({ username: "", password: "", pgName: "", location: "", mobileNumber: "" });
      setAuthMode("REGISTRATION_SUCCESS");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleAdminLogin(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const overview = await request(
        "/api/admin/owners",
        adminLoginForm,
        {},
        "Admin login failed. Check the admin username and password."
      );
      setPendingOwners(overview.pending);
      setApprovedOwners(overview.approved);
      sessionStorage.setItem("haven-auth-session", JSON.stringify({ type: "admin", credentials: adminLoginForm }));
      setAdminCredentials(adminLoginForm);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function refreshAdminOwners() {
    const overview = await request(
      "/api/admin/owners",
      adminCredentials,
      {},
      "Admin authentication failed. Please sign in again."
    );
    setPendingOwners(overview.pending);
    setApprovedOwners(overview.approved);
  }

  async function handleAdminRefresh() {
    setLoading(true);
    setError("");
    try {
      await refreshAdminOwners();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function approveOwner(ownerId) {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request(
        `/api/admin/owners/${ownerId}/approve`,
        adminCredentials,
        { method: "PUT" },
        "Admin authentication failed. Please sign in again."
      );
      const approvedOwner = pendingOwners.find((owner) => owner.id === ownerId);
      if (approvedOwner) {
        setPendingOwners((owners) => owners.filter((owner) => owner.id !== ownerId));
        setApprovedOwners((owners) => [...owners, approvedOwner]);
      }
      setNotice("Owner registration approved. They can now sign in.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function removeOwner(owner) {
    const confirmed = window.confirm(
      `Remove ${owner.pgName} (${owner.username}) from Haven? They will no longer be able to sign in. Existing resident and payment records will be preserved.`
    );
    if (!confirmed) return;

    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request(
        `/api/admin/owners/${owner.id}`,
        adminCredentials,
        { method: "DELETE" },
        "Admin authentication failed. Please sign in again."
      );
      setPendingOwners((owners) => owners.filter((item) => item.id !== owner.id));
      setApprovedOwners((owners) => owners.filter((item) => item.id !== owner.id));
      setNotice(`${owner.pgName} was removed. Existing resident and payment records were preserved.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function openProfileSettings() {
    setProfileOpen(true);
    setLoading(true);
    setError("");
    setNotice("");
    try {
      const profile = await request("/api/owner/profile", credentials);
      setProfileForm({ username: profile.username, pgName: profile.pgName, location: profile.location || "" });
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveProfile(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request("/api/owner/profile", credentials, {
        method: "PUT",
        body: JSON.stringify(profileForm)
      });
      const updatedCredentials = { ...credentials, username: profileForm.username.trim() };
      sessionStorage.setItem("haven-auth-session", JSON.stringify({ type: "owner", credentials: updatedCredentials }));
      setCredentials(updatedCredentials);
      setLoginForm(updatedCredentials);
      setProfileOpen(false);
      setNotice("Username, PG name, and location updated.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    sessionStorage.removeItem("haven-auth-session");
    setCredentials(null);
    setResidentPage({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true
    });
    setResidentPageIndex(0);
    setDashboardSummary(null);
    setRooms([]);
    setRoomForm({ roomNumber: "", sharing: "" });
    setMonthlyPaymentReport(null);
    setMonthlyPaymentsOpen(false);
    setLoginForm({ username: "", password: "" });
    setProfileForm({ username: "", pgName: "", location: "" });
    setSearchText("");
    setSelectedStatus("ACTIVE");
    setForm(emptyForm);
    setEditingId(null);
    setFormOpen(false);
    setError("");
    setNotice("");
    setAuthMode("LANDING");
    setDocumentResident(null);
    setDocumentPreview(null);
  }

  function handleSearch(event) {
    event.preventDefault();
    setResidentPageIndex(0);
  }

  async function addRoom(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setNotice("");
    try {
      const room = await request(`${RESIDENTS_API}/rooms`, credentials, {
        method: "POST",
        body: JSON.stringify({ ...roomForm, sharing: Number(roomForm.sharing) })
      });
      await loadDashboard(credentials);
      setRoomForm({ roomNumber: "", sharing: "" });
      setNotice(`Room ${room.roomNumber} added.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function deleteRoom(roomNumber) {
    if (!window.confirm(`Delete room ${roomNumber}? This cannot be undone.`)) return;
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request(`${RESIDENTS_API}/rooms/${encodeURIComponent(roomNumber)}`, credentials, { method: "DELETE" });
      await loadDashboard(credentials);
      setNotice(`Room ${roomNumber} deleted.`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  function changePaymentPeriod(event) {
    setSelectedPaymentPeriod(event.target.value);
  }

  function shiftPaymentPeriod(offset) {
    const [year, month] = selectedPaymentPeriod.split("-").map(Number);
    const shifted = new Date(year, month - 1 + offset, 1);
    const period = `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, "0")}`;
    const firstPaymentPeriod = dashboardSummary?.firstPaymentPeriod || "";
    if ((!firstPaymentPeriod || period >= firstPaymentPeriod) && period <= currentPaymentPeriod) {
      setSelectedPaymentPeriod(period);
    }
  }

  function showResidentsByStatus(status) {
    setMonthlyPaymentsOpen(false);
    setSelectedStatus(status);
    setSearchText("");
    setResidentPageIndex(0);
    setError("");
    setNotice("");
  }

  function showMonthlyPayments() {
    setEmptyBedsOpen(false);
    setMonthlyPaymentsOpen((open) => !open);
    setSearchText("");
    setError("");
    setNotice("");
  }

  function goToDashboardHome() {
    setEmptyBedsOpen(false);
    setMonthlyPaymentsOpen(false);
    setSelectedStatus("ACTIVE");
    setSearchText("");
    setResidentPageIndex(0);
    setError("");
    setNotice("");
  }

  function startCreate() {
    setEditingId(null);
    setForm(emptyForm);
    setPhotoFile(null);
    setIdentityProofFile(null);
    setDocumentPreview(null);
    setError("");
    setNotice("");
    setFormOpen(true);
  }

  function startEdit(resident) {
    setEditingId(resident.id);
    setForm(residentToForm(resident));
    setPhotoFile(null);
    setIdentityProofFile(null);
    setDocumentPreview(null);
    setError("");
    setNotice("");
    setFormOpen(true);
  }

  function closeForm() {
    setFormOpen(false);
    setForm(emptyForm);
    setEditingId(null);
    setPhotoFile(null);
    setIdentityProofFile(null);
    setDocumentPreview(null);
    setDocumentError("");
    setError("");
  }

  async function handleSave(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setNotice("");
    const payload = {
      ...form,
      sharing: Number(form.sharing),
      rent: Number(form.rent),
      advanceAmount: Number(form.advanceAmount)
    };
    let residentId = editingId;
    const creatingResident = editingId === null;
    const uploadingFiles = Boolean(photoFile || identityProofFile);
    let residentDetailsSaved = false;
    let failedStep = "saving resident details";
    try {
      if (residentId === null) {
        const createdResident = await request(`${RESIDENTS_API}/post`, credentials, {
          method: "POST",
          body: JSON.stringify(payload)
        });
        residentId = createdResident.id;
        setEditingId(residentId);
      } else {
        await request(`${RESIDENTS_API}/${residentId}`, credentials, {
          method: "PUT",
          body: JSON.stringify(payload)
        });
      }
      residentDetailsSaved = true;

      for (const [type, file] of [["photo", photoFile], ["identity-proof", identityProofFile]]) {
        if (!file) continue;
        failedStep = type === "photo" ? "uploading the resident photo" : "uploading the identity proof";
        if (file.size > 5 * 1024 * 1024) {
          throw new Error("Choose files smaller than 5 MB.");
        }
        const body = new FormData();
        body.append("file", file);
        await request(`${RESIDENTS_API}/${residentId}/${type}`, credentials, {
          method: "POST",
          body
        });
        if (type === "photo") {
          setPhotoFile(null);
          if (photoInput.current) photoInput.current.value = "";
        } else {
          setIdentityProofFile(null);
          if (identityProofInput.current) identityProofInput.current.value = "";
        }
      }

      failedStep = "refreshing the resident list";
      await refreshOwnerData(credentials);
      setNotice(
        creatingResident
          ? uploadingFiles
            ? "Resident details and selected files submitted successfully."
            : "Resident details submitted successfully."
          : uploadingFiles
            ? "Resident details updated and selected files uploaded successfully."
            : "Resident details updated successfully."
      );
      setFormOpen(false);
      setForm(emptyForm);
      setEditingId(null);
      setPhotoFile(null);
      setIdentityProofFile(null);
      setDocumentPreview(null);
    } catch (requestError) {
      if (residentDetailsSaved) {
        let refreshError = null;
        try {
          await refreshOwnerData(credentials);
        } catch (followUpError) {
          refreshError = followUpError;
        }
        const message = failedStep === "refreshing the resident list"
          ? `Resident details and uploaded files were saved, but refreshing the resident list failed: ${requestError.message}`
          : `Resident details were saved, but ${failedStep} failed: ${requestError.message}`;
        setError(refreshError ? `${message} The resident list could not be refreshed: ${refreshError.message}` : message);
      } else {
        setError(requestError.message);
      }
    } finally {
      setLoading(false);
    }
  }

  async function markVacated(resident) {
    if (!window.confirm(`Mark ${resident.name} in room ${resident.roomNumber} as vacated? Their record will be retained.`)) {
      return;
    }
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request(`${RESIDENTS_API}/${resident.id}`, credentials, { method: "DELETE" });
      setNotice("Resident marked as vacated successfully. Their history has been preserved.");
      await refreshOwnerData(credentials);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function permanentlyDeleteResident(resident) {
    if (!window.confirm(`Permanently delete ${resident.name} in room ${resident.roomNumber}? This also removes their documents and cannot be undone.`)) {
      return;
    }
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request(`${RESIDENTS_API}/${resident.id}/permanent`, credentials, { method: "DELETE" });
      setNotice("Resident and their documents permanently deleted.");
      await refreshOwnerData(credentials);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function openRentPayments(resident, year = null, month = null) {
    const joiningYear = Number(resident.joiningDate.slice(0, 4));
    const requestedYear = year ?? Math.max(new Date().getFullYear(), joiningYear);
    setLoading(true);
    setError("");
    setNotice("");
    try {
      const payments = await request(
        `${RESIDENTS_API}/${resident.id}/rent-payments?year=${requestedYear}`,
        credentials);
      setRentPaymentResident(resident);
      setRentPaymentYear(requestedYear);
      setRentPayments(payments);
      if (month) {
        const selectedPayment = payments.find((payment) => payment.month === month);
        setPaymentDraft({
          month,
          amount: selectedPayment?.amount ? String(selectedPayment.amount) : String(resident.rent),
          paidDate: selectedPayment?.paidDate || new Date().toISOString().slice(0, 10),
          method: selectedPayment?.method || "CASH"
        });
      } else {
        setPaymentDraft(null);
      }
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function saveRentPayment(event) {
    event.preventDefault();
    if (!rentPaymentResident || !paymentDraft) return;
    setLoading(true);
    setError("");
    setNotice("");
    try {
      const result = await request(
        `${RESIDENTS_API}/${rentPaymentResident.id}/rent-payments/${rentPaymentYear}/${paymentDraft.month}`,
        credentials,
        {
          method: "PUT",
          body: JSON.stringify({
            amount: Number(paymentDraft.amount),
            paidDate: paymentDraft.paidDate,
            method: paymentDraft.method
          })
        });
      setRentPayments((payments) => [
        ...payments.filter((payment) => payment.month !== result.month),
        result
      ].sort((first, second) => first.month - second.month));
      await refreshOwnerData(credentials);
      setPaymentDraft(null);
      setNotice(result.wasAlreadyPaid
        ? "Rent payment details updated."
        : "Rent payment saved successfully.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function viewDocument(resident, documentType) {
    setLoading(true);
    setError("");
    setDocumentError("");
    setDocumentPreview(null);
    if (!formOpen) setViewerResident(resident);
    const documentName = documentType === "photo" ? "Resident photo" : "Identity proof";
    try {
      const response = await fetch(
        apiUrl(`${RESIDENTS_API}/${resident.id}/${documentType === "photo" ? "photo" : "identity-proof"}`),
        { headers: { Authorization: `Basic ${encodeBasicCredentials(credentials)}` } }
      );
      if (!response.ok) {
        if (response.status === 401) throw new Error("Your sign-in is no longer valid. Sign out and sign in again.");
        if (response.status === 404) throw new Error("This resident does not have that file uploaded yet.");
        if (response.status === 410) throw new Error("The file was uploaded before but is no longer available on the server. Please edit the resident and upload it again.");
        let detail = "";
        try {
          const errorBody = await response.json();
          detail = errorBody.detail || errorBody.message || "";
        } catch {
          detail = "";
        }
        throw new Error(detail || `Could not open document (${response.status}).`);
      }
      const blob = await response.blob();
      const preview = { url: URL.createObjectURL(blob), contentType: blob.type, title: documentName };
      if (formOpen) {
        setDocumentPreview(preview);
      } else {
        setViewerResident(resident);
        setDocumentPreview(preview);
      }
    } catch (requestError) {
      setDocumentError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  function updateForm(event) {
    const { name, value } = event.target;
    const nextValue = name === "mobileNumber" ? value.replace(/\D/g, "").slice(0, 10) : value;
    setForm((current) => ({ ...current, [name]: nextValue }));
  }

  if (authRestoring) {
    return <main className="session-loading" role="status" aria-label="Loading">
      <span className="loading-spinner" aria-hidden="true" />
    </main>;
  }

  if (!credentials) {
    if (adminCredentials) {
      return (
        <main className="admin-page-shell">
          <header className="admin-topbar">
            <a className="brand" href="/" aria-label="Haven home">
              <span className="brand-mark">H</span>
              <span>haven<span className="brand-period">.</span></span>
            </a>
            <button type="button" className="button button-light" onClick={() => {
              sessionStorage.removeItem("haven-auth-session");
              setAdminCredentials(null);
              setAdminLoginForm({ username: "", password: "" });
              setPendingOwners([]);
              setApprovedOwners([]);
              setAuthMode("LANDING");
              setError("");
              setNotice("");
            }}>Admin sign out</button>
          </header>
          <section className="admin-page-content">
            <div className="admin-page-heading">
              <div>
                <p className="eyebrow">ADMIN PORTAL</p>
                <h1>Owner management</h1>
                <p className="muted">Review registrations and manage owner access across Haven.</p>
              </div>
              <button type="button" className="button button-light" disabled={loading} onClick={handleAdminRefresh}>{loading ? "Refreshing..." : "Refresh owners"}</button>
            </div>
            {error && <div className="alert alert-error" role="alert">{error}</div>}
            {notice && <div className="alert alert-success alert-centered" role="status">{notice}</div>}
            <div className="admin-total-card">
              <span>Total registered owners</span>
              <strong>{pendingOwners.length + approvedOwners.length}</strong>
              <small>{pendingOwners.length} waiting · {approvedOwners.length} approved</small>
            </div>
            <div className="admin-owner-columns">
              {[
                { title: "Waiting approval", owners: pendingOwners, status: "PENDING", empty: "No registrations are waiting for approval." },
                { title: "Approved owners", owners: approvedOwners, status: "APPROVED", empty: "No approved owners yet." }
              ].map(({ title, owners, status, empty }) => (
                <section className="admin-owner-column" key={status} aria-label={`${title}: ${owners.length}`}>
                  <header className="admin-owner-column-heading">
                    <h2>{title}</h2>
                    <span>{owners.length}</span>
                  </header>
                  {owners.length === 0 ? (
                    <div className="admin-empty-state"><p>{empty}</p></div>
                  ) : (
                    <div className="admin-owner-list">
                      {owners.map((owner, index) => (
                        <article className="admin-owner-card" key={owner.id}>
                          <div className="admin-owner-number">Owner No. {index + 1}</div>
                          <div className="admin-owner-card-main">
                            <div className="admin-owner-avatar" aria-hidden="true">{owner.pgName.trim().charAt(0).toUpperCase()}</div>
                            <div className="admin-owner-info">
                              <div className="admin-owner-title">
                                <strong>{owner.pgName}</strong>
                                <span className={`owner-status ${status === "PENDING" ? "owner-status-pending" : "owner-status-approved"}`}>{status === "PENDING" ? "Pending" : "Approved"}</span>
                              </div>
                              <div className="admin-owner-details">
                                <span><small>USERNAME</small>{owner.username}</span>
                                <span><small>MOBILE NUMBER</small>{owner.mobileNumber}</span>
                                <span><small>LOCATION / AREA</small>{owner.location || "Not provided"}</span>
                              </div>
                            </div>
                          </div>
                          <div className="admin-owner-actions">
                            {status === "PENDING" && <button type="button" className="button button-primary" disabled={loading} onClick={() => approveOwner(owner.id)}>Approve owner</button>}
                            <button type="button" className="button button-danger" disabled={loading} onClick={() => removeOwner(owner)}>Remove owner</button>
                          </div>
                        </article>
                      ))}
                    </div>
                  )}
                </section>
              ))}
            </div>
          </section>
        </main>
      );
    }

    if (authMode === "LANDING") {
      return (
        <main className="public-landing">
          <header className="landing-header">
            <a className="brand" href="/" aria-label="Haven home">
              <span className="brand-mark">H</span>
              <span>haven<span className="brand-period">.</span></span>
            </a>
          </header>
          <section className="landing-hero">
            <div className="landing-copy">
              <p className="eyebrow">PG MANAGEMENT, MADE SIMPLE</p>
              <h1>Run your PG with <span>less paperwork.</span></h1>
              <p className="muted">Keep resident records, room availability, and month-by-month rent payments together in one clear, secure dashboard.</p>
              <div className="landing-hero-actions">
                <button type="button" className="button button-primary" onClick={() => setAuthMode("SETUP")}>＋ Register your PG</button>
                <button type="button" className="button button-primary" onClick={() => setAuthMode("LOGIN")}>Owner login</button>
                <button type="button" className="button button-primary" onClick={() => setAuthMode("ADMIN_LOGIN")}>Admin login</button>
              </div>
              <p className="landing-approval-note">New owner accounts require administrator approval.</p>
            </div>
            <div className="landing-preview" aria-label="Dashboard features preview">
              <div className="landing-preview-top"><span /><span /><span /><strong>Owner dashboard</strong></div>
              <div className="landing-preview-stat"><span>Active residents</span><strong>All in one place</strong></div>
              <div className="landing-preview-row"><span className="landing-preview-icon">⌂</span><div><strong>Rooms & beds</strong><small>See available beds at a glance</small></div><b>→</b></div>
              <div className="landing-preview-row"><span className="landing-preview-icon landing-preview-rent">₹</span><div><strong>Monthly rent</strong><small>Track paid and pending months</small></div><b>→</b></div>
              <div className="landing-preview-row"><span className="landing-preview-icon landing-preview-docs">✓</span><div><strong>Resident records</strong><small>Store key details and documents</small></div><b>→</b></div>
            </div>
          </section>
          <section className="landing-features" aria-labelledby="landing-features-title">
            <p className="eyebrow">MADE FOR EVERYDAY PG WORK</p>
            <h2 id="landing-features-title">The essentials, all together</h2>
            <div className="landing-feature-grid">
              <article><span>01</span><h3>Resident management</h3><p>Keep resident details, joining status, and important documents organised.</p></article>
              <article><span>02</span><h3>Rooms and bed availability</h3><p>Set room sharing and see how many beds are available before assigning residents.</p></article>
              <article><span>03</span><h3>Monthly rent tracking</h3><p>Check outstanding rent by month and record payments for each month separately.</p></article>
            </div>
          </section>
          <footer className="landing-footer">Haven · A clearer way to manage your PG</footer>
        </main>
      );
    }

    return (
      <main className="login-shell">
        <section className="login-card">
          <a className="brand" href="/" aria-label="Haven home" onClick={(event) => {
            event.preventDefault();
            setAuthMode("LANDING");
            setError("");
            setNotice("");
          }}>
            <span className="brand-mark">H</span>
            <span>haven<span className="brand-period">.</span></span>
          </a>
          <p className="eyebrow">{authMode === "ADMIN_LOGIN" ? "ADMIN PORTAL" : authMode === "SETUP" ? "PG REGISTRATION" : "OWNER PORTAL"}</p>
          <h1>{authMode === "REGISTRATION_SUCCESS" ? registrationApproved ? "Admin approved." : "Registration received." : authMode === "SETUP" ? "Register your PG" : authMode === "ADMIN_LOGIN" ? "Admin login" : "Owner login"}</h1>
          {authMode === "REGISTRATION_SUCCESS" && <p className="muted">{registrationApproved ? `${registrationSuccessPgName} is approved. You can now log in.` : `Thank you for registering ${registrationSuccessPgName}. Please wait for administrator approval.`}</p>}
          {authMode === "REGISTRATION_SUCCESS" && !registrationApproved && <div className="alert alert-success" role="status">Waiting for an administrator to approve your registration. This page will update when approved.</div>}
          {authMode === "REGISTRATION_SUCCESS" && registrationApproved && <button type="button" className="button button-primary button-wide" onClick={() => {
            setAuthMode("LOGIN");
            setRegistrationApproved(false);
            setError("");
            setNotice("");
          }}>Go to sign in</button>}
          {error && <div className="alert alert-error" role="alert">{error}</div>}
          {notice && <div className="alert alert-success alert-centered" role="status">{notice}</div>}
          {authMode === "LOGIN" && <form className="stack-form" onSubmit={handleLogin}>
            <label>
              Username
              <input
                autoComplete="username"
                required
                value={loginForm.username}
                onChange={(event) => setLoginForm({ ...loginForm, username: event.target.value })}
              />
            </label>
            <label>
              Password
              <SecretInput
                autoComplete="current-password"
                required
                value={loginForm.password}
                onChange={(event) => setLoginForm({ ...loginForm, password: event.target.value })}
              />
            </label>
            <button className="button button-primary button-wide" disabled={loading}>
              {loading ? "Signing in…" : "Sign in"}
            </button>
          </form>}
          {authMode === "ADMIN_LOGIN" && <form className="stack-form" onSubmit={handleAdminLogin}>
            <label>Admin username<input autoComplete="username" required value={adminLoginForm.username} onChange={(event) => setAdminLoginForm({ ...adminLoginForm, username: event.target.value })} /></label>
            <label>Admin password<SecretInput autoComplete="current-password" required value={adminLoginForm.password} onChange={(event) => setAdminLoginForm({ ...adminLoginForm, password: event.target.value })} /></label>
            <button className="button button-primary button-wide" disabled={loading}>{loading ? "Signing in…" : "Sign in as admin"}</button>
          </form>}
          {authMode === "SETUP" && <form className="stack-form" onSubmit={handleSetupOwner}>
            <label>PG name<input maxLength="100" required value={setupForm.pgName} onChange={(event) => setSetupForm({ ...setupForm, pgName: event.target.value })} /></label>
            <label>Location / area<input maxLength="150" placeholder="e.g. Madhapur, Hyderabad" required value={setupForm.location} onChange={(event) => setSetupForm({ ...setupForm, location: event.target.value })} /></label>
            <label>Username<input autoComplete="username" minLength="3" maxLength="50" required value={setupForm.username} onChange={(event) => setSetupForm({ ...setupForm, username: event.target.value })} /></label>
            <label className="password-field">Password<SecretInput minLength={12} maxLength="128" autoComplete="new-password" aria-describedby="registration-password-help" required value={setupForm.password} onChange={(event) => {
              setSetupForm({ ...setupForm, password: event.target.value });
            }} />
              <span id="registration-password-help" className={`field-help${setupForm.password.length > 0 && setupForm.password.length < 12 ? " field-help-error" : ""}`} aria-live="polite">
                {setupForm.password.length >= 12
                  ? "Password length meets the 12-character minimum."
                  : setupForm.password.length > 0
                    ? `Use at least 12 characters (${setupForm.password.length}/12).`
                    : "Use at least 12 characters."}
              </span>
            </label>
            <label>
              Mobile number
              <input type="tel" placeholder="9876543210" inputMode="numeric" maxLength="10" pattern="[6-9][0-9]{9}" required value={setupForm.mobileNumber} onChange={(event) => setSetupForm({ ...setupForm, mobileNumber: event.target.value.replace(/\D/g, "").slice(0, 10) })} />
            </label>
            <button className="button button-primary button-wide" disabled={loading}>{loading ? "Creating account…" : "Create owner account"}</button>
          </form>}
          <button type="button" className="text-action auth-back-button" onClick={() => { setAuthMode("LANDING"); setError(""); setNotice(""); }}>← Back</button>
        </section>
      </main>
    );
  }

  const editingResident = residents.find((resident) => resident.id === editingId) || null;
  const roomAvailability = rooms
    .filter((room) => room.roomNumber.toLocaleLowerCase().includes(roomSearchText.trim().toLocaleLowerCase()))
    .sort((first, second) => first.roomNumber.localeCompare(second.roomNumber, undefined, { numeric: true }));
  const totalEmptyBeds = dashboardSummary?.availableBeds ?? 0;
  const firstPaymentPeriod = dashboardSummary?.firstPaymentPeriod || "";
  const pendingPayments = monthlyPaymentReport?.residents.filter((payment) => payment.remaining > 0) || [];
  const monthlyMemberCount = monthlyPaymentReport?.residents.length || 0;
  const monthlyEmptyBeds = dashboardSummary
    ? Math.max(0, dashboardSummary.totalBeds - monthlyMemberCount)
    : null;
  const currentPaymentPeriod = getCurrentPaymentPeriod();
  const ownerWorkspaceOpen = !monthlyPaymentsOpen && !emptyBedsOpen;

  return (
    <main className="dashboard">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Haven home" onClick={(event) => {
          event.preventDefault();
          goToDashboardHome();
        }}>
          <span className="brand-mark">H</span>
          <span>haven<span className="brand-period">.</span></span>
        </a>
        <div className="topbar-right">
          <span className="owner-label"><span className="online-dot" /> {profileForm.pgName || "Owner dashboard"}</span>
          <button className="button button-primary dashboard-nav-button" type="button" onClick={goToDashboardHome}>Home</button>
          <button className="button button-light dashboard-nav-button" onClick={openProfileSettings}>PG settings</button>
          <button className="button button-light dashboard-nav-button" onClick={logout}>Sign out</button>
        </div>
      </header>

      <div className="page-content">
        {ownerWorkspaceOpen && <section className="page-heading">
          <div>
            <p className="eyebrow">YOUR PROPERTY</p>
            <h1>Resident overview</h1>
            <p className="muted">A clear view of the people who call your PG home.</p>
          </div>
          <div className="page-heading-actions">
            <button className="button button-primary dashboard-nav-button" onClick={startCreate}>＋ Add resident</button>
            <button className="button button-primary dashboard-nav-button" type="button" aria-expanded={monthlyPaymentsOpen} aria-controls="monthly-payments" onClick={showMonthlyPayments}>
              {monthlyPaymentsOpen ? "Hide payments" : "Manage payments"}
            </button>
            <button className="button button-primary dashboard-nav-button" type="button" aria-expanded={emptyBedsOpen} aria-controls="room-availability" onClick={() => {
              setMonthlyPaymentsOpen(false);
              setEmptyBedsOpen((open) => !open);
            }}>
              {emptyBedsOpen ? "Hide rooms/beds" : "Manage rooms/beds"}
            </button>
          </div>
        </section>}

        {error && <div className="alert alert-error" role="alert">{error}</div>}
        {notice && <div className="alert alert-success alert-centered" role="status">{notice}</div>}

        {ownerWorkspaceOpen && dashboardLoading && !dashboardSummary && (
          <div className="dashboard-loading-note" role="status">Loading your latest dashboard totals…</div>
        )}
        {ownerWorkspaceOpen && dashboardSummary && <section className="stats-grid" aria-label="Property overview">
          <button className={`stat-card stat-card-button${selectedStatus === "ACTIVE" ? " stat-card-selected" : ""}`} type="button" aria-pressed={selectedStatus === "ACTIVE"} onClick={() => showResidentsByStatus("ACTIVE")} disabled={loading}>
            <span className="stat-icon icon-active">✓</span>
            <div><p className="stat-label">Active residents</p><strong>{dashboardSummary.activeResidents}</strong></div>
          </button>
          <button className={`stat-card stat-card-button${selectedStatus === "VACATED" ? " stat-card-selected" : ""}`} type="button" aria-pressed={selectedStatus === "VACATED"} onClick={() => showResidentsByStatus("VACATED")} disabled={loading}>
            <span className="stat-icon icon-vacated">↗</span>
            <div><p className="stat-label">Vacated residents</p><strong>{dashboardSummary.vacatedResidents}</strong></div>
          </button>
          <article className="stat-card stat-card-static">
            <span className="stat-icon icon-total">▦</span>
            <div><p className="stat-label">Total rooms</p><strong>{dashboardSummary.totalRooms}</strong></div>
          </article>
          <article className="stat-card stat-card-static">
            <span className="stat-icon icon-total">▤</span>
            <div><p className="stat-label">Total beds</p><strong>{dashboardSummary.totalBeds}</strong></div>
          </article>
          <article className="stat-card stat-card-static">
            <span className="stat-icon icon-active">●</span>
            <div><p className="stat-label">Occupied beds</p><strong>{dashboardSummary.occupiedBeds}</strong></div>
          </article>
          <article className="stat-card stat-card-static">
            <span className="stat-icon icon-active">＋</span>
            <div><p className="stat-label">Available beds</p><strong>{dashboardSummary.availableBeds}</strong></div>
          </article>
          <article className="stat-card stat-card-static">
            <span className="stat-icon icon-pending">₹</span>
            <div><p className="stat-label">Pending rent for {currentMonthName}</p><strong>{formatCurrency(dashboardSummary.pendingRentAmount)}</strong></div>
          </article>
          <article className="stat-card stat-card-static">
            <span className="stat-icon icon-active">₹</span>
            <div><p className="stat-label">Collection for {currentMonthName}</p><strong>{formatCurrency(dashboardSummary.monthlyRentCollection)}</strong></div>
          </article>
        </section>}

        {emptyBedsOpen && <section className="room-availability panel" id="room-availability" aria-labelledby="room-availability-title">
          <div className="room-availability-heading">
            <div>
              <p className="eyebrow">ROOMS</p>
              <h2 id="room-availability-title">Manage rooms/beds</h2>
            </div>
          </div>
          <div className="total-beds-summary">
            <span>Total empty beds</span>
            <strong>{totalEmptyBeds}</strong>
          </div>
          <div className="room-availability-filters">
            <form className="room-add-form" onSubmit={addRoom}>
              <label>
                Room number
                <input
                  maxLength="30"
                  required
                  value={roomForm.roomNumber}
                  onChange={(event) => setRoomForm({ ...roomForm, roomNumber: event.target.value })}
                  placeholder="e.g. 101"
                />
              </label>
              <label>
                Sharing capacity
                <select required value={roomForm.sharing} onChange={(event) => setRoomForm({ ...roomForm, sharing: event.target.value })}>
                  <option value="">Select sharing</option>
                  <option value="1">1 sharing</option>
                  <option value="2">2 sharing</option>
                  <option value="3">3 sharing</option>
                  <option value="4">4 sharing</option>
                </select>
              </label>
              <button className="button button-primary" type="submit" disabled={loading}>{loading ? "Adding…" : "Add room"}</button>
            </form>
            <label className="room-search">
              Search rooms
              <input value={roomSearchText} onChange={(event) => setRoomSearchText(event.target.value)} placeholder="e.g. 101" />
            </label>
          </div>
          {roomAvailability.length === 0 ? (
            <p className="room-availability-empty">
              {roomSearchText.trim()
                ? "No rooms match this search."
                : "No rooms added yet. Add rooms with their sharing capacity to track vacancies."}
            </p>
          ) : (
            <div className="room-availability-grid">
              {roomAvailability.map((room) => (
                <article className={`room-availability-card${room.availableBeds > 0 ? " room-has-vacancy" : ""}`} key={room.roomNumber.toLocaleLowerCase()}>
                  <div className="room-card-heading">
                      <strong>Room {room.roomNumber}{room.sharing ? ` (${sharingLabel(room.sharing)})` : ""}</strong>
                      {room.configured && (
                      <button
                        type="button"
                        className="room-delete-button"
                        disabled={loading || room.residentCount > 0}
                        title={room.residentCount > 0 ? "This room has resident records and cannot be deleted." : `Delete room ${room.roomNumber}`}
                        onClick={() => deleteRoom(room.roomNumber)}
                      >Delete room</button>
                    )}
                  </div>
                  <p>{room.occupied} occupied · {room.sharing ? `${room.availableBeds} available` : "capacity not configured"}</p>
                </article>
              ))}
            </div>
          )}
        </section>}

        {!emptyBedsOpen && <>
        {monthlyPaymentsOpen ? (
          <section className="panel monthly-payments-panel" id="monthly-payments" aria-labelledby="monthly-payments-title">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">MONTHLY RENT</p>
                <h2 id="monthly-payments-title">
                  Manage payments
                  {monthlyPaymentReport && <span className="count-pill">{new Date(monthlyPaymentReport.year, monthlyPaymentReport.month - 1).toLocaleDateString(undefined, { month: "long", year: "numeric" })}</span>}
                </h2>
                <p className="muted">Review rent month by month. Record each month separately, even when collecting multiple months together.</p>
              </div>
              <div className="payment-period-selector">
                <span>Payment month</span>
                <div className="payment-period-controls">
                  <button type="button" className="button button-light" aria-label="Previous payment month" disabled={loading || (firstPaymentPeriod && selectedPaymentPeriod <= firstPaymentPeriod)} onClick={() => shiftPaymentPeriod(-1)}>‹</button>
                  <input
                    type="month"
                    aria-label="Select payment month"
                    value={selectedPaymentPeriod}
                    min={firstPaymentPeriod || undefined}
                    max={currentPaymentPeriod}
                    onChange={changePaymentPeriod}
                    disabled={loading}
                  />
                  <button type="button" className="button button-light" aria-label="Next payment month" disabled={loading || selectedPaymentPeriod >= currentPaymentPeriod} onClick={() => shiftPaymentPeriod(1)}>›</button>
                </div>
              </div>
            </div>
            {monthlyPaymentsLoading && !monthlyPaymentReport && (
              <p className="room-availability-empty" role="status">Loading payments for this month…</p>
            )}
            {monthlyPaymentReport && <>
              <div className="monthly-payment-totals" aria-label="Monthly rent totals">
                <article className="monthly-total-residents">
                  <span>Members in selected month</span>
                  <strong>{monthlyMemberCount}</strong>
                </article>
                <article className="monthly-total-filled-beds">
                  <span>Filled beds</span>
                  <strong>{monthlyMemberCount}</strong>
                </article>
                <article className="monthly-total-empty-beds">
                  <span>Empty beds</span>
                  <strong>{monthlyEmptyBeds ?? "—"}</strong>
                </article>
                <article><span>Total rent</span><strong>{formatCurrency(monthlyPaymentReport.totalRent)}</strong></article>
                <article><span>Rent collected</span><strong>{formatCurrency(monthlyPaymentReport.totalCollected)}</strong></article>
                <article><span>Remaining to collect</span><strong>{formatCurrency(monthlyPaymentReport.totalRemaining)}</strong></article>
              </div>
              <p className="monthly-bed-note">Bed occupancy uses the selected month’s members and your configured room capacity.</p>
              <div className="payment-list-heading">
                <div>
                  <p className="eyebrow">FOLLOW-UP</p>
                  <h3>Pending payments <span className="count-pill">{pendingPayments.length}</span></h3>
                </div>
                <p className="muted">Residents disappear from this list when the selected month is fully paid.</p>
              </div>
              {pendingPayments.length === 0 ? (
                <p className="room-availability-empty">No pending payments for this month.</p>
              ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr><th>RESIDENT</th><th>ROOM / SHARING</th><th>MOBILE</th><th>RENT DATE</th><th>MONTHLY RENT</th><th>ACTION</th></tr>
                  </thead>
                  <tbody>
                    {pendingPayments.map((payment) => (
                      <tr key={payment.residentId}>
                        <td data-label="Resident">{payment.residentName}</td>
                        <td data-label="Room / sharing"><span className="room-tag">Room {payment.roomNumber} · {payment.sharing ? sharingLabel(payment.sharing) : "sharing not set"}</span></td>
                        <td data-label="Mobile">{payment.mobileNumber || "—"}</td>
                        <td data-label="Rent date">{payment.joiningDate ? Number(payment.joiningDate.slice(-2)) : "—"}</td>
                        <td data-label="Monthly rent">{formatCurrency(payment.rent)}</td>
                        <td data-label="Action"><button type="button" className="button button-primary record-payment-button" disabled={loading} onClick={() => {
                          const [year, month] = selectedPaymentPeriod.split("-").map(Number);
                          openRentPayments({
                            id: payment.residentId,
                            name: payment.residentName,
                            roomNumber: payment.roomNumber,
                            rent: payment.rent,
                            joiningDate: payment.joiningDate
                          }, year, month);
                        }}>{payment.collected > 0 ? "Update payment" : "Record payment"}</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              )}
            </>}
          </section>
        ) : (
        <section className="workspace">
          <article className="panel resident-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">DIRECTORY</p>
                <h2>{searchText.trim() ? "Search results" : selectedStatus === "ACTIVE" ? "Active members" : "Vacated members"} <span className="count-pill">{residentPage.totalElements}</span></h2>
              </div>
              <div className="directory-actions">
                <form className="search-form" onSubmit={handleSearch}>
                  <span aria-hidden="true" className="search-icon">⌕</span>
                  <input
                    aria-label="Search residents"
                    placeholder="Search by name, father or room"
                    maxLength="100"
                    value={searchText}
                    onChange={(event) => {
                      setSearchText(event.target.value);
                      setResidentPageIndex(0);
                    }}
                  />
                  {searchText && <button type="button" className="clear-search" onClick={() => {
                    setSearchText("");
                    setResidentPageIndex(0);
                  }} aria-label="Clear search">×</button>}
                  <button type="submit" className="button button-search" disabled={residentLoading}>Search</button>
                </form>
              </div>
            </div>

            {profileOpen && <div className="dialog-backdrop">
              <section className="resident-dialog panel document-viewer" role="dialog" aria-modal="true" aria-labelledby="pg-settings-title">
                <div className="panel-heading">
                  <div><p className="eyebrow">PROPERTY SETTINGS</p><h2 id="pg-settings-title">PG details</h2></div>
                  <button className="icon-button" onClick={() => setProfileOpen(false)} aria-label="Close PG settings">×</button>
                </div>
                {error && <div className="alert alert-error dialog-alert" role="alert">{error}</div>}
                <form className="stack-form password-change-form" onSubmit={handleSaveProfile}>
                  <label>Username<input autoComplete="username" minLength="3" maxLength="50" pattern="[A-Za-z0-9._-]+" required value={profileForm.username || ""} onChange={(event) => setProfileForm({ ...profileForm, username: event.target.value })} /></label>
                  <label>PG name<input maxLength="100" required value={profileForm.pgName} onChange={(event) => setProfileForm({ ...profileForm, pgName: event.target.value })} /></label>
                  <label>Location / area<input maxLength="150" required value={profileForm.location || ""} onChange={(event) => setProfileForm({ ...profileForm, location: event.target.value })} /></label>
                  <p className="muted">Your username is used for your next sign-in. Contact your administrator if you need to reset your password.</p>
                  <div className="form-footer-actions">
                    <button type="button" className="button button-light" onClick={() => setProfileOpen(false)}>Cancel</button>
                    <button type="submit" className="button button-primary" disabled={loading}>{loading ? "Saving…" : "Save PG settings"}</button>
                  </div>
                </form>
              </section>
            </div>}

            {residentLoading && residents.length === 0 ? (
              <div className="directory-loading" role="status">Loading residents…</div>
            ) : residents.length === 0 ? (
              <div className="empty-state">
                <div className="empty-illustration">⌂</div>
                <h3>{searchText.trim() ? "No matching residents" : "Your directory is ready"}</h3>
                <p>{searchText.trim() ? "Try another name or room number." : "Add your first resident to start building your directory."}</p>
                {!searchText.trim() && <button className="button button-primary" onClick={startCreate}>＋ Add a resident</button>}
              </div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr><th>RESIDENT</th><th>MOBILE</th><th>ROOM / SHARING</th><th>RENT / MONTH</th><th>JOINED</th><th>STATUS</th><th>VACATED DATE</th><th>RESIDENT DETAILS</th><th><span className="sr-only">Actions</span></th></tr>
                  </thead>
                  <tbody>
                    {residents.map((resident) => (
                      <tr key={resident.id}>
                        <td data-label="Resident">
                          <div className="resident-cell">
                            <span className="avatar">{resident.name.trim().charAt(0).toUpperCase()}</span>
                            <span><strong>{resident.name}</strong><small>Father: {resident.fatherName}</small></span>
                          </div>
                          <div className="mobile-resident-summary">
                            <span>Room {resident.roomNumber} · {resident.sharing ? sharingLabel(resident.sharing) : "sharing not set"}</span>
                            <span>{resident.mobileNumber || "Mobile not added"}</span>
                          </div>
                          <details className="mobile-resident-details">
                            <summary>All details</summary>
                            <div className="mobile-resident-full-details">
                              <div><span>Mobile</span><strong>{resident.mobileNumber || "—"}</strong></div>
                              <div><span>Father</span><strong>{resident.fatherName}</strong></div>
                              <div><span>Rent / month</span><strong>₹{Number(resident.rent).toLocaleString("en-IN")}</strong></div>
                              <div><span>Advance</span><strong>₹{Number(resident.advanceAmount).toLocaleString("en-IN")}</strong></div>
                              <div><span>Joined</span><strong>{resident.joiningDate}</strong></div>
                              <div><span>Status</span><strong>{resident.status === "ACTIVE" ? "Active" : "Vacated"}</strong></div>
                              <div><span>Vacated date</span><strong>{resident.vacatedDate || "—"}</strong></div>
                              <div><span>Permanent address</span><strong>{resident.permanentAddress}</strong></div>
                              <div><span>Company address</span><strong>{resident.companyAddress}</strong></div>
                              <div className="details-documents">
                                {resident.hasPhoto
                                  ? <button className="text-action" onClick={() => viewDocument(resident, "photo")}>View photo</button>
                                  : <span className="missing-document">No photo</span>}
                                {resident.hasIdentityProof
                                  ? <button className="text-action" onClick={() => viewDocument(resident, "identity-proof")}>View proof</button>
                                  : <span className="missing-document">No proof</span>}
                              </div>
                            </div>
                          </details>
                        </td>
                        <td data-label="Mobile">{resident.mobileNumber || "—"}</td>
                        <td data-label="Room / sharing"><span className="room-tag">{resident.roomNumber} · {resident.sharing ? sharingLabel(resident.sharing) : "sharing not set"}</span></td>
                        <td data-label="Rent / month" className="rent-cell">₹{Number(resident.rent).toLocaleString("en-IN")}</td>
                        <td data-label="Joined">{resident.joiningDate}</td>
                        <td data-label="Status"><span className={`status-badge ${resident.status === "ACTIVE" ? "status-active" : "status-vacated"}`}><span />{resident.status === "ACTIVE" ? "Active" : "Vacated"}</span></td>
                        <td data-label="Vacated date">{resident.vacatedDate || "—"}</td>
                        <td data-label="Resident details" className="details-cell">
                          <div><span>Father</span><strong>{resident.fatherName}</strong></div>
                          <div><span>Advance</span><strong>₹{Number(resident.advanceAmount).toLocaleString("en-IN")}</strong></div>
                          <div><span>Permanent</span><strong>{resident.permanentAddress}</strong></div>
                          <div><span>Company</span><strong>{resident.companyAddress}</strong></div>
                          <div className="details-documents">
                            {resident.hasPhoto
                              ? <button className="text-action" onClick={() => viewDocument(resident, "photo")}>View photo</button>
                              : <span className="missing-document">No photo</span>}
                            {resident.hasIdentityProof
                              ? <button className="text-action" onClick={() => viewDocument(resident, "identity-proof")}>View proof</button>
                              : <span className="missing-document">No proof</span>}
                          </div>
                        </td>
                        <td data-label="Actions">
                          <div className="row-actions">
                            <button type="button" className="resident-action-button action-history" onClick={() => openRentPayments(resident)}>Rent history</button>
                            <button type="button" className="resident-action-button action-edit" onClick={() => startEdit(resident)}>Edit</button>
                            {resident.status === "ACTIVE" && <button type="button" className="resident-action-button action-vacate" onClick={() => markVacated(resident)}>Vacate</button>}
                            <button type="button" className="resident-action-button action-delete" onClick={() => permanentlyDeleteResident(resident)} disabled={loading} title="Permanently delete resident">Delete</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {!residentLoading && residentPage.totalElements > 0 && <div className="table-pagination">
              <span>
                Showing {residentPage.page * residentPage.size + 1}–{residentPage.page * residentPage.size + residents.length}
                {" "}of {residentPage.totalElements} residents
              </span>
              <div>
                <button
                  type="button"
                  className="button button-light"
                  disabled={residentPage.first || residentLoading}
                  onClick={() => setResidentPageIndex((page) => Math.max(page - 1, 0))}
                >Previous</button>
                <span>Page {residentPage.page + 1} of {Math.max(residentPage.totalPages, 1)}</span>
                <button
                  type="button"
                  className="button button-light"
                  disabled={residentPage.last || residentLoading}
                  onClick={() => setResidentPageIndex((page) => page + 1)}
                >Next</button>
              </div>
            </div>}
          </article>

        </section>
        )}
        </>}

        {rentPaymentResident && (
          <div className="dialog-backdrop" onClick={() => setRentPaymentResident(null)}>
            <section className="resident-dialog panel rent-payment-dialog" role="dialog" aria-modal="true" aria-labelledby="rent-payment-title" onClick={(event) => event.stopPropagation()}>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">MONTHLY RENT</p>
                  <h2 id="rent-payment-title">{rentPaymentResident.name} · Room {rentPaymentResident.roomNumber}</h2>
                  <p className="muted">Record rent paid by month and keep a payment history.</p>
                </div>
                <button className="icon-button" onClick={() => setRentPaymentResident(null)} aria-label="Close rent history">×</button>
              </div>
              {error && <div className="alert alert-error dialog-alert" role="alert">{error}</div>}
              <div className="rent-year-selector">
                <button className="button button-light" disabled={loading || rentPaymentYear <= Number(rentPaymentResident.joiningDate.slice(0, 4))} onClick={() => openRentPayments(rentPaymentResident, rentPaymentYear - 1)}>Previous year</button>
                <strong>{rentPaymentYear}</strong>
                <button className="button button-light" disabled={loading || rentPaymentYear >= new Date().getFullYear()} onClick={() => openRentPayments(rentPaymentResident, rentPaymentYear + 1)}>Next year</button>
              </div>
              <p className="rent-history-start">Rent history starts from {new Date(`${rentPaymentResident.joiningDate}T00:00:00`).toLocaleString("en", { month: "long", year: "numeric" })}, the resident’s joining month.</p>
              <div className="rent-month-grid">
                {Array.from({ length: 12 }, (_, index) => index)
                  .filter((index) => {
                    const joiningDate = new Date(`${rentPaymentResident.joiningDate}T00:00:00`);
                    return rentPaymentYear > joiningDate.getFullYear()
                      || (rentPaymentYear === joiningDate.getFullYear() && index >= joiningDate.getMonth());
                  })
                  .map((index) => {
                  const month = index + 1;
                  const payment = rentPayments.find((record) => record.month === month);
                  const draftOpen = paymentDraft?.month === month;
                  return (
                    <article className={`rent-month-card${payment?.paid ? " rent-month-paid" : ""}`} key={month}>
                      <div className="rent-month-heading">
                        <strong>{new Date(rentPaymentYear, index, 1).toLocaleString("en", { month: "long" })}</strong>
                        <span className={`rent-payment-status${payment?.paid ? " is-paid" : ""}`}>{payment?.paid ? "Paid" : "Unpaid"}</span>
                      </div>
                      {payment?.paid ? (
                        <div className="rent-payment-detail">
                          <span>₹{Number(payment.amount).toLocaleString("en-IN")} · {payment.method?.replace("_", " ")}</span>
                          <small>Paid {payment.paidDate}</small>
                        </div>
                      ) : <p className="rent-payment-detail">Monthly rent: ₹{Number(rentPaymentResident.rent).toLocaleString("en-IN")}</p>}
                      {draftOpen ? (
                        <form className="rent-payment-form" onSubmit={saveRentPayment}>
                          <label>Amount (₹)<input type="number" min="0.01" step="0.01" required value={paymentDraft.amount} onChange={(event) => setPaymentDraft({ ...paymentDraft, amount: event.target.value })} /></label>
                          <label>Paid date<input type="date" required value={paymentDraft.paidDate} onChange={(event) => setPaymentDraft({ ...paymentDraft, paidDate: event.target.value })} /></label>
                          <label>Payment method<select required value={paymentDraft.method} onChange={(event) => setPaymentDraft({ ...paymentDraft, method: event.target.value })}><option value="CASH">Cash</option><option value="UPI">UPI</option><option value="BANK_TRANSFER">Bank transfer</option><option value="OTHER">Other</option></select></label>
                          <div className="rent-card-actions">
                            <button type="button" className="button button-light" onClick={() => setPaymentDraft(null)}>Cancel</button>
                            <button type="submit" className="button button-primary" disabled={loading}>{loading ? "Saving…" : "Save payment"}</button>
                          </div>
                        </form>
                      ) : <button type="button" className="text-action" disabled={loading} onClick={() => setPaymentDraft({
                        month,
                        amount: payment?.amount ? String(payment.amount) : String(rentPaymentResident.rent),
                        paidDate: payment?.paidDate || new Date().toISOString().slice(0, 10),
                        method: payment?.method || "CASH"
                      })}>{payment?.paid ? "Edit payment" : "Mark rent paid"}</button>}
                    </article>
                  );
                })}
              </div>
            </section>
          </div>
        )}

        {formOpen && (
          <div className="dialog-backdrop">
            <section className="resident-dialog panel" role="dialog" aria-modal="true" aria-labelledby="resident-form-title">
              <div className="panel-heading resident-dialog-heading">
                <div>
                  <p className="eyebrow">{editingId === null ? "NEW RECORD" : `EDITING RESIDENT #${editingId}`}</p>
                  <h2 id="resident-form-title">{editingId === null ? "Add a resident" : "Edit resident details"}</h2>
                  <p className="muted">Enter resident details and attach their photo and identity proof.</p>
                </div>
                <button className="icon-button" onClick={closeForm} disabled={loading} aria-label="Close resident form">×</button>
              </div>
              {error && <div className="alert alert-error dialog-alert" role="alert">{error}</div>}
              <form className="resident-form" onSubmit={handleSave}>
                <div className="resident-fields">
                  <div className="form-row">
                    <label>Full name<input name="name" maxLength="100" required value={form.name} onChange={updateForm} /></label>
                    <label>Father’s name<input name="fatherName" maxLength="100" required value={form.fatherName} onChange={updateForm} /></label>
                  </div>
                  <label>
                    Mobile number
                    <input type="tel" name="mobileNumber" inputMode="numeric" maxLength="10" pattern="[6-9][0-9]{9}" required value={form.mobileNumber} onChange={updateForm} />
                  </label>
                  <div className="form-row">
                    <label>Room number<input name="roomNumber" maxLength="30" required value={form.roomNumber} onChange={updateForm} /></label>
                    <label>Sharing size
                      <select name="sharing" required value={form.sharing} onChange={updateForm}>
                        <option value="">Select sharing</option>
                        <option value="1">1 sharing</option>
                        <option value="2">2 sharing</option>
                        <option value="3">3 sharing</option>
                        <option value="4">4 sharing</option>
                      </select>
                    </label>
                  </div>
                  <label>Joining date<input type="date" name="joiningDate" required value={form.joiningDate} onChange={updateForm} /></label>
                  <div className="form-row">
                    <label>Monthly rent (₹)<input type="number" name="rent" min="0.01" step="0.01" required value={form.rent} onChange={updateForm} /></label>
                    <label>Advance (₹)<input type="number" name="advanceAmount" min="0" step="0.01" required value={form.advanceAmount} onChange={updateForm} /></label>
                  </div>
                  <label>Permanent address<textarea name="permanentAddress" rows="2" maxLength="1000" required value={form.permanentAddress} onChange={updateForm} /></label>
                  <label>Company address<textarea name="companyAddress" rows="2" maxLength="1000" required value={form.companyAddress} onChange={updateForm} /></label>
                </div>

                <section className="attachments-section" aria-labelledby="attachments-heading">
                  <div className="attachments-heading">
                    <div>
                      <p className="eyebrow">DOCUMENTS</p>
                      <h3 id="attachments-heading">Photo & identity proof</h3>
                    </div>
                    <span className="private-chip"><span>◆</span> Private</span>
                  </div>
                  <p className="attachments-help">Add files now or when editing this resident. JPEG/PNG photos and JPEG/PNG/PDF proofs, up to 5 MB each.</p>
                  <div className="attachment-grid">
                    <div className="attachment-card">
                      <span className="attachment-icon">▧</span>
                      <span className="attachment-title">Resident photo</span>
                      <span className="attachment-subtitle">{photoFile?.name || (editingResident?.hasPhoto ? "Photo already saved" : "No photo selected")}</span>
                      <label className="file-picker">
                        <span>{photoFile ? "Choose a different photo" : "Choose photo"}</span>
                        <input ref={photoInput} type="file" accept="image/jpeg,image/png" onChange={(event) => setPhotoFile(event.target.files?.[0] || null)} />
                      </label>
                      {editingResident?.hasPhoto && editingId !== null && (
                        <button type="button" className="text-action attachment-view" onClick={() => viewDocument(editingResident, "photo")}>View saved photo</button>
                      )}
                    </div>
                    <div className="attachment-card">
                      <span className="attachment-icon proof-icon">▤</span>
                      <span className="attachment-title">Identity proof</span>
                      <span className="attachment-subtitle">{identityProofFile?.name || (editingResident?.hasIdentityProof ? "Proof already saved" : "No proof selected")}</span>
                      <label className="file-picker">
                        <span>{identityProofFile ? "Choose a different file" : "Choose proof"}</span>
                        <input ref={identityProofInput} type="file" accept="image/jpeg,image/png,application/pdf" onChange={(event) => setIdentityProofFile(event.target.files?.[0] || null)} />
                      </label>
                      {editingResident?.hasIdentityProof && editingId !== null && (
                        <button type="button" className="text-action attachment-view" onClick={() => viewDocument(editingResident, "identity-proof")}>View saved proof</button>
                      )}
                    </div>
                  </div>
                  {documentError && <div className="alert alert-error document-message" role="alert">{documentError}</div>}
                  {documentPreview && (
                    <div className="document-preview">
                      <div className="preview-title">{documentPreview.title}</div>
                      {documentPreview.contentType.startsWith("image/") ? (
                        <img src={documentPreview.url} alt={documentPreview.title} />
                      ) : (
                        <iframe title={documentPreview.title} src={documentPreview.url} />
                      )}
                    </div>
                  )}
                </section>
                <div className="resident-form-footer">
                  <p className="form-privacy"><span>◆</span> Only the signed-in owner can view saved files.</p>
                  <div className="form-footer-actions">
                    <button type="button" className="button button-light" onClick={closeForm} disabled={loading}>Cancel</button>
                    <button type="submit" className="button button-primary" disabled={loading}>
                      {loading ? "Saving resident…" : editingId === null ? "Save resident" : "Save changes"}
                    </button>
                  </div>
                </div>
              </form>
            </section>
          </div>
        )}

        {viewerResident && (
          <div className="dialog-backdrop" onClick={() => { setViewerResident(null); setDocumentPreview(null); setDocumentError(""); }}>
            <section className="document-viewer panel" role="dialog" aria-modal="true" aria-labelledby="document-viewer-title" onClick={(event) => event.stopPropagation()}>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">PRIVATE RESIDENT FILE</p>
                  <h2 id="document-viewer-title">{viewerResident.name} · {documentPreview?.title || "Resident document"}</h2>
                  <p className="muted">Room {viewerResident.roomNumber}. Visible to the signed-in owner only.</p>
                </div>
                <button className="icon-button" onClick={() => { setViewerResident(null); setDocumentPreview(null); setDocumentError(""); }} aria-label="Close file">×</button>
              </div>
              {documentError
                ? <div className="alert alert-error document-message" role="alert">{documentError}</div>
                : documentPreview
                  ? <div className="document-preview">
                    {documentPreview.contentType.startsWith("image/") ? (
                      <img src={documentPreview.url} alt={documentPreview.title} />
                    ) : (
                      <iframe title={documentPreview.title} src={documentPreview.url} />
                    )}
                  </div>
                  : <div className="document-message muted" role="status">Opening document…</div>}
            </section>
          </div>
        )}

        <footer className="page-footer"><span>haven<span className="brand-period">.</span></span> Resident management, made simpler.</footer>
      </div>
    </main>
  );
}

export default App;
