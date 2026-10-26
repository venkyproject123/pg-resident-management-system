import { useEffect, useRef, useState } from "react";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
const apiUrl = (path) => `${API_BASE_URL}${path}`;
const RESIDENTS_API = "/api/residents";
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
    mobileNumber: resident.mobileNumber,
    roomNumber: resident.roomNumber,
    sharing: resident.sharing ? String(resident.sharing) : "",
    rent: String(resident.rent),
    advanceAmount: String(resident.advanceAmount),
    joiningDate: resident.joiningDate,
    permanentAddress: resident.permanentAddress,
    companyAddress: resident.companyAddress
  };
}

function phoneForForm(number = "") {
  return number.startsWith("+91") ? number.slice(3) : number;
}

function sharingLabel(sharing) {
  return sharing === 1 ? "Single sharing" : `${sharing} sharing`;
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
  const [authMode, setAuthMode] = useState("LOGIN");
  const [setupForm, setSetupForm] = useState({ username: "", password: "", pgName: "", location: "", mobileNumber: "" });
  const [registrationSuccessPgName, setRegistrationSuccessPgName] = useState("");
  const [registrationCredentials, setRegistrationCredentials] = useState(null);
  const [registrationApproved, setRegistrationApproved] = useState(false);
  const [recoveryForm, setRecoveryForm] = useState({ username: "", whatsappNumber: "", otp: "", newPassword: "" });
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileForm, setProfileForm] = useState({ pgName: "", location: "", whatsappNumber: "", otp: "" });
  const [residents, setResidents] = useState([]);
  const [allResidents, setAllResidents] = useState([]);
  const [residentTotals, setResidentTotals] = useState({ active: 0, vacated: 0 });
  const [searchText, setSearchText] = useState("");
  const [roomSearchText, setRoomSearchText] = useState("");
  const [onlyAvailableRooms, setOnlyAvailableRooms] = useState(false);
  const [selectedStatus, setSelectedStatus] = useState("ALL");
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
  const [viewerResident, setViewerResident] = useState(null);
  const [rentPaymentResident, setRentPaymentResident] = useState(null);
  const [rentPaymentYear, setRentPaymentYear] = useState(new Date().getFullYear());
  const [rentPayments, setRentPayments] = useState([]);
  const [paymentDraft, setPaymentDraft] = useState(null);
  const photoInput = useRef(null);
  const identityProofInput = useRef(null);

  useEffect(() => {
    return () => {
      if (documentPreview?.url) URL.revokeObjectURL(documentPreview.url);
    };
  }, [documentPreview]);

  useEffect(() => {
    if (savedSession) {
      let mounted = true;
      setLoading(true);
      const restoreSession = async () => {
        try {
          if (savedSession.type === "owner") {
            const [, profile] = await Promise.all([
              loadResidents(savedSession.credentials),
              request("/api/owner/profile", savedSession.credentials)
            ]);
            if (!mounted) return;
            setProfileForm({ ...profile, whatsappNumber: phoneForForm(profile.whatsappNumber || ""), otp: "" });
          } else {
            const [pending, approved] = await Promise.all([
              request("/api/admin/owners/pending", savedSession.credentials, {}, "Admin authentication failed. Please sign in again."),
              request("/api/admin/owners/approved", savedSession.credentials, {}, "Admin authentication failed. Please sign in again.")
            ]);
            if (!mounted) return;
            setPendingOwners(pending);
            setApprovedOwners(approved);
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

    let mounted = true;
    fetch(apiUrl("/api/owner/setup-status"))
      .then(async (response) => {
        if (!response.ok) throw new Error(`Could not check owner setup status (${response.status}).`);
        return response.json();
      })
      .then((result) => {
        if (!mounted) return;
        if (result.setupRequired) setAuthMode("SETUP");
      })
      .catch((requestError) => {
        if (mounted) setError(requestError.message);
      });
    return () => { mounted = false; };
  }, []);

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

  async function loadResidents(owner, keyword = "", status = "ALL") {
    const allResidents = await request(`${RESIDENTS_API}/all`, owner);
    setAllResidents(allResidents);
    const query = keyword.trim().toLocaleLowerCase();
    const data = allResidents.filter((resident) => {
      const matchesStatus = status === "ALL" || resident.status === status;
      const matchesQuery = !query || [
        resident.name,
        resident.fatherName,
        resident.roomNumber
      ].some((value) => value?.toLocaleLowerCase().includes(query));
      return matchesStatus && matchesQuery;
    });
    setResidents(data);
    setResidentTotals({
      active: allResidents.filter((resident) => resident.status === "ACTIVE").length,
      vacated: allResidents.filter((resident) => resident.status === "VACATED").length
    });
    return data;
  }

  async function handleLogin(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const [, profile] = await Promise.all([
        loadResidents(loginForm),
        request("/api/owner/profile", loginForm)
      ]);
      setProfileForm({ ...profile, whatsappNumber: phoneForForm(profile.whatsappNumber || ""), otp: "" });
      sessionStorage.setItem("haven-auth-session", JSON.stringify({ type: "owner", credentials: loginForm }));
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
      const [pending, approved] = await Promise.all([
        request(
          "/api/admin/owners/pending",
          adminLoginForm,
          {},
          "Admin login failed. Check the admin username and password."
        ),
        request(
          "/api/admin/owners/approved",
          adminLoginForm,
          {},
          "Admin login failed. Check the admin username and password."
        )
      ]);
      setPendingOwners(pending);
      setApprovedOwners(approved);
      sessionStorage.setItem("haven-auth-session", JSON.stringify({ type: "admin", credentials: adminLoginForm }));
      setAdminCredentials(adminLoginForm);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function refreshAdminOwners() {
    const [pending, approved] = await Promise.all([
      request("/api/admin/owners/pending", adminCredentials, {}, "Admin authentication failed. Please sign in again."),
      request("/api/admin/owners/approved", adminCredentials, {}, "Admin authentication failed. Please sign in again.")
    ]);
    setPendingOwners(pending);
    setApprovedOwners(approved);
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

  async function handleRequestResetOtp() {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await publicRequest("/api/owner/password-reset/otp", {
        username: recoveryForm.username,
        whatsappNumber: recoveryForm.whatsappNumber
      });
      setNotice("If the username and mobile number match, an OTP has been sent to the registered mobile number.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirmPasswordReset(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      await publicRequest("/api/owner/password-reset", recoveryForm);
      setAuthMode("LOGIN");
      setLoginForm({ username: recoveryForm.username, password: "" });
      setRecoveryForm({ username: "", whatsappNumber: "", otp: "", newPassword: "" });
      setNotice("Password changed. Sign in with your new password.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleRequestProfileOtp() {
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await request("/api/owner/profile/otp", credentials, {
        method: "POST",
        body: JSON.stringify({ whatsappNumber: profileForm.whatsappNumber })
      });
      setNotice("Verification OTP sent to the mobile number entered above. It expires in 5 minutes.");
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
      setProfileForm({ ...profileForm, otp: "" });
      const updatedCredentials = { ...credentials, username: profileForm.username.trim() };
      sessionStorage.setItem("haven-auth-session", JSON.stringify({ type: "owner", credentials: updatedCredentials }));
      setCredentials(updatedCredentials);
      setLoginForm(updatedCredentials);
      setProfileOpen(false);
      setNotice("Username, PG name, and verified mobile number updated.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  function logout() {
    sessionStorage.removeItem("haven-auth-session");
    setCredentials(null);
    setAllResidents([]);
    setLoginForm({ username: "", password: "" });
    setResidents([]);
    setResidentTotals({ active: 0, vacated: 0 });
    setSearchText("");
    setSelectedStatus("ALL");
    setForm(emptyForm);
    setEditingId(null);
    setFormOpen(false);
    setError("");
    setNotice("");
    setDocumentResident(null);
    setDocumentPreview(null);
  }

  async function handleSearch(event) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await loadResidents(credentials, searchText, selectedStatus);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function showResidentsByStatus(status) {
    setSelectedStatus(status);
    setSearchText("");
    setLoading(true);
    setError("");
    setNotice("");
    try {
      await loadResidents(credentials, "", status);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
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

      await loadResidents(credentials, searchText, selectedStatus);
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
        try {
          await loadResidents(credentials, searchText, selectedStatus);
        } catch {
          setError(`Resident details were saved, but a follow-up request failed: ${requestError.message}`);
          setLoading(false);
          return;
        }
        setError(`Resident details were saved, but a file or follow-up request failed: ${requestError.message}`);
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
      await loadResidents(credentials, searchText, selectedStatus);
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
      await loadResidents(credentials, searchText, selectedStatus);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function openRentPayments(resident, year = null) {
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
      setPaymentDraft(null);
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
      setPaymentDraft(null);
      setNotice(result.wasAlreadyPaid
        ? "Rent payment details updated. No new WhatsApp confirmation was sent."
        : result.whatsappNotificationSent
          ? "Rent payment saved and WhatsApp confirmation sent."
          : "Rent payment saved, but WhatsApp confirmation was not sent. Check the WhatsApp setup and resident mobile number.");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  async function viewDocument(resident, documentType) {
    setLoading(true);
    setError("");
    if (formOpen) setDocumentPreview(null);
    const documentName = documentType === "photo" ? "Resident photo" : "Identity proof";
    try {
      const response = await fetch(
        apiUrl(`${RESIDENTS_API}/${resident.id}/${documentType === "photo" ? "photo" : "identity-proof"}`),
        { headers: { Authorization: `Basic ${encodeBasicCredentials(credentials)}` } }
      );
      if (!response.ok) {
        if (response.status === 401) throw new Error("Your sign-in is no longer valid. Sign out and sign in again.");
        if (response.status === 404) throw new Error("This resident does not have that file uploaded yet.");
        throw new Error(`Could not open document (${response.status}).`);
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
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }

  function updateForm(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  if (authRestoring) {
    return <main className="session-loading" role="status">Restoring your secure session…</main>;
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
            {notice && <div className="alert alert-success" role="status">{notice}</div>}
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
                          {status === "PENDING" && <button type="button" className="button button-primary" disabled={loading} onClick={() => approveOwner(owner.id)}>Approve owner</button>}
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

    return (
      <main className="login-shell">
        <section className="login-card">
          <a className="brand" href="/" aria-label="Haven home">
            <span className="brand-mark">H</span>
            <span>haven<span className="brand-period">.</span></span>
          </a>
          <p className="eyebrow">{authMode === "ADMIN_LOGIN" ? "ADMIN PORTAL" : "OWNER PORTAL"}</p>
          <h1>{authMode === "REGISTRATION_SUCCESS" ? registrationApproved ? "Admin approved." : "Registration received." : authMode === "SETUP" ? "Create your owner account." : authMode === "FORGOT" ? "Recover your account." : authMode === "ADMIN_LOGIN" ? "Admin sign in." : "Welcome back."}</h1>
          <p className="muted">{authMode === "REGISTRATION_SUCCESS" ? registrationApproved ? `${registrationSuccessPgName} is approved. You can now log in.` : `Thank you for registering in Haven Resident Management System. ${registrationSuccessPgName} registered successfully. Please wait a few seconds for admin approval.` : authMode === "SETUP" ? "Create your PG owner account. An administrator must approve it before you can sign in." : authMode === "FORGOT" ? "Verify the mobile number registered to your account to reset your password." : authMode === "ADMIN_LOGIN" ? "Sign in to review and approve owner registrations." : "Sign in to securely manage your residents."}</p>
          {authMode === "REGISTRATION_SUCCESS" && !registrationApproved && <div className="alert alert-success" role="status">Waiting for an administrator to approve your registration. This page will update when approved.</div>}
          {authMode === "REGISTRATION_SUCCESS" && registrationApproved && <button type="button" className="button button-primary button-wide" onClick={() => {
            setAuthMode("LOGIN");
            setRegistrationApproved(false);
            setError("");
            setNotice("");
          }}>Go to sign in</button>}
          {error && <div className="alert alert-error" role="alert">{error}</div>}
          {notice && <div className="alert alert-success" role="status">{notice}</div>}
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
            <label>Mobile number<input type="tel" placeholder="9876543210" inputMode="numeric" maxLength="10" pattern="[6-9][0-9]{9}" required value={setupForm.mobileNumber} onChange={(event) => setSetupForm({ ...setupForm, mobileNumber: event.target.value })} /></label>
            <button className="button button-primary button-wide" disabled={loading}>{loading ? "Creating account…" : "Create owner account"}</button>
          </form>}
          {authMode === "FORGOT" && <form className="stack-form" onSubmit={handleConfirmPasswordReset}>
            <label>Username<input autoComplete="username" required value={recoveryForm.username} onChange={(event) => setRecoveryForm({ ...recoveryForm, username: event.target.value })} /></label>
            <label>Mobile number<input type="tel" placeholder="9876543210" inputMode="numeric" maxLength="10" pattern="[6-9][0-9]{9}" required value={recoveryForm.whatsappNumber} onChange={(event) => setRecoveryForm({ ...recoveryForm, whatsappNumber: event.target.value })} /></label>
            <button type="button" className="button button-light" disabled={loading || !recoveryForm.username || !recoveryForm.whatsappNumber} onClick={handleRequestResetOtp}>Send password reset OTP</button>
            <label>OTP<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength="6" required value={recoveryForm.otp} onChange={(event) => setRecoveryForm({ ...recoveryForm, otp: event.target.value })} /></label>
            <label>New password<SecretInput minLength="12" maxLength="128" autoComplete="new-password" required value={recoveryForm.newPassword} onChange={(event) => setRecoveryForm({ ...recoveryForm, newPassword: event.target.value })} /></label>
            <button className="button button-primary button-wide" disabled={loading}>Reset password</button>
          </form>}
          {authMode === "LOGIN" && <>
            <button type="button" className="text-action" onClick={() => { setAuthMode("SETUP"); setError(""); setNotice(""); }}>Register your PG</button>
            <button type="button" className="text-action" onClick={() => { setAuthMode("FORGOT"); setError(""); setNotice(""); }}>Forgot password?</button>
            <button type="button" className="text-action" onClick={() => { setAuthMode("ADMIN_LOGIN"); setError(""); setNotice(""); }}>Admin login</button>
            <p className="login-footnote">Your password is stored as a one-way hash. Password resets use an OTP sent to your registered mobile number.</p>
          </>}
          {authMode !== "LOGIN" && authMode !== "REGISTRATION_SUCCESS" && <button type="button" className="text-action" onClick={() => { setAuthMode("LOGIN"); setError(""); setNotice(""); }}>Back to sign in</button>}
        </section>
        <aside className="login-aside">
          <div className="aside-orb orb-one" />
          <div className="aside-orb orb-two" />
          <div className="aside-content">
            <span className="aside-kicker">A calmer way to manage your PG</span>
            <h2>Your residents.<br />All in one place.</h2>
            <p>Keep everyday records clear, organised, and in your hands.</p>
            <div className="aside-note"><span className="lock-icon">↗</span> Private owner access</div>
          </div>
          <span className="aside-caption">RESIDENT MANAGEMENT · MADE SIMPLE</span>
        </aside>
      </main>
    );
  }

  const editingResident = residents.find((resident) => resident.id === editingId) || null;
  const totalMembers = residentTotals.active + residentTotals.vacated;
  const roomGroups = new Map();
  allResidents.forEach((resident) => {
    const key = resident.roomNumber.trim().toLocaleLowerCase();
    if (!roomGroups.has(key)) roomGroups.set(key, []);
    roomGroups.get(key).push(resident);
  });
  const roomAvailability = Array.from(roomGroups.values())
    .map((roomResidents) => {
      const capacity = roomResidents.find((resident) => Number.isInteger(resident.sharing))?.sharing;
      if (!capacity) return null;
      const occupied = roomResidents.filter((resident) => resident.status === "ACTIVE").length;
      return {
        roomNumber: roomResidents[0].roomNumber,
        capacity,
        occupied,
        vacant: Math.max(capacity - occupied, 0)
      };
    })
    .filter((room) => room !== null)
    .filter((room) => room.roomNumber.toLocaleLowerCase().includes(roomSearchText.trim().toLocaleLowerCase()))
    .filter((room) => !onlyAvailableRooms || room.vacant > 0)
    .sort((first, second) => first.roomNumber.localeCompare(second.roomNumber, undefined, { numeric: true }));

  return (
    <main className="dashboard">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Haven home">
          <span className="brand-mark">H</span>
          <span>haven<span className="brand-period">.</span></span>
        </a>
        <div className="topbar-right">
          <span className="owner-label"><span className="online-dot" /> {profileForm.pgName || "Owner dashboard"}</span>
          <button className="button button-light" onClick={() => setProfileOpen(true)}>PG settings</button>
          <button className="button button-light" onClick={logout}>Sign out</button>
        </div>
      </header>

      <div className="page-content">
        <section className="page-heading">
          <div>
            <p className="eyebrow">YOUR PROPERTY</p>
            <h1>Resident overview</h1>
            <p className="muted">A clear view of the people who call your PG home.</p>
          </div>
          <button className="button button-primary" onClick={startCreate}>＋ Add resident</button>
        </section>

        {error && <div className="alert alert-error" role="alert">{error}</div>}
        {notice && <div className="alert alert-success" role="status">{notice}</div>}

        <section className="stats-grid" aria-label="Resident totals">
          <button className={`stat-card stat-card-button${selectedStatus === "ALL" ? " stat-card-selected" : ""}`} type="button" aria-pressed={selectedStatus === "ALL"} onClick={() => showResidentsByStatus("ALL")} disabled={loading}>
            <span className="stat-icon icon-total">⌂</span>
            <div><p className="stat-label">Total members in PG</p><strong>{totalMembers}</strong></div>
          </button>
          <button className={`stat-card stat-card-button${selectedStatus === "ACTIVE" ? " stat-card-selected" : ""}`} type="button" aria-pressed={selectedStatus === "ACTIVE"} onClick={() => showResidentsByStatus("ACTIVE")} disabled={loading}>
            <span className="stat-icon icon-active">✓</span>
            <div><p className="stat-label">Total active members</p><strong>{residentTotals.active}</strong></div>
          </button>
          <button className={`stat-card stat-card-button${selectedStatus === "VACATED" ? " stat-card-selected" : ""}`} type="button" aria-pressed={selectedStatus === "VACATED"} onClick={() => showResidentsByStatus("VACATED")} disabled={loading}>
            <span className="stat-icon icon-vacated">↗</span>
            <div><p className="stat-label">Total vacated members</p><strong>{residentTotals.vacated}</strong></div>
          </button>
        </section>

        <section className="room-availability panel" aria-labelledby="room-availability-title">
          <div className="room-availability-heading">
            <div>
              <p className="eyebrow">ROOMS</p>
              <h2 id="room-availability-title">Available beds</h2>
              <p className="muted">Capacity is calculated from each room’s sharing size and active residents.</p>
            </div>
          </div>
          <div className="room-availability-filters">
            <label className="room-search">
              Search by room number
              <input value={roomSearchText} onChange={(event) => setRoomSearchText(event.target.value)} placeholder="e.g. 101" />
            </label>
            <label className="availability-toggle">
              <input type="checkbox" checked={onlyAvailableRooms} onChange={(event) => setOnlyAvailableRooms(event.target.checked)} />
              Show rooms with empty beds only
            </label>
          </div>
          {roomAvailability.length === 0 ? (
            <p className="room-availability-empty">
              {allResidents.length === 0
                ? "Add residents and their room sharing size to track available beds."
                : allResidents.some((resident) => Number.isInteger(resident.sharing))
                  ? "No rooms match this search. Try another room number or show all rooms."
                  : "Set a sharing size (1–4) while adding or editing a resident to see room availability."}
            </p>
          ) : (
            <div className="room-availability-grid">
              {roomAvailability.map((room) => (
                <article className={`room-availability-card${room.vacant > 0 ? " room-has-vacancy" : ""}`} key={room.roomNumber.toLocaleLowerCase()}>
                  <div className="room-card-heading">
                    <strong>Room {room.roomNumber}</strong>
                    <span>{room.vacant > 0 ? `${room.vacant} bed${room.vacant === 1 ? "" : "s"} available` : "Full"}</span>
                  </div>
                  <p>{room.occupied} of {room.capacity} beds occupied</p>
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="workspace">
          <article className="panel resident-panel">
            <div className="panel-heading">
              <div>
                <p className="eyebrow">DIRECTORY</p>
                <h2>{searchText.trim() ? "Search results" : selectedStatus === "ALL" ? "All members" : selectedStatus === "ACTIVE" ? "Active members" : "Vacated members"} <span className="count-pill">{residents.length}</span></h2>
              </div>
              <div className="directory-actions">
                <form className="search-form" onSubmit={handleSearch}>
                  <span aria-hidden="true" className="search-icon">⌕</span>
                  <input
                    aria-label="Search residents"
                    placeholder="Search by name or room number"
                    value={searchText}
                    onChange={(event) => setSearchText(event.target.value)}
                  />
                  {searchText && <button type="button" className="clear-search" onClick={() => setSearchText("")} aria-label="Clear search">×</button>}
                  <button type="submit" className="button button-search" disabled={loading}>Search</button>
                </form>
              </div>
            </div>

            {profileOpen && <div className="dialog-backdrop">
              <section className="resident-dialog panel document-viewer" role="dialog" aria-modal="true" aria-labelledby="pg-settings-title">
                <div className="panel-heading">
                  <div><p className="eyebrow">PROPERTY SETTINGS</p><h2 id="pg-settings-title">PG details and rent reminders</h2></div>
                  <button className="icon-button" onClick={() => setProfileOpen(false)} aria-label="Close PG settings">×</button>
                </div>
                {error && <div className="alert alert-error dialog-alert" role="alert">{error}</div>}
                <form className="stack-form password-change-form" onSubmit={handleSaveProfile}>
                  <label>Username<input autoComplete="username" minLength="3" maxLength="50" pattern="[A-Za-z0-9._-]+" required value={profileForm.username || ""} onChange={(event) => setProfileForm({ ...profileForm, username: event.target.value })} /></label>
                  <label>PG name<input maxLength="100" required value={profileForm.pgName} onChange={(event) => setProfileForm({ ...profileForm, pgName: event.target.value })} /></label>
                  <label>Location / area<input maxLength="150" required value={profileForm.location || ""} onChange={(event) => setProfileForm({ ...profileForm, location: event.target.value })} /></label>
                  <label>Mobile number<input type="tel" placeholder="9876543210" inputMode="numeric" maxLength="10" pattern="[6-9][0-9]{9}" required value={phoneForForm(profileForm.whatsappNumber || "")} onChange={(event) => setProfileForm({ ...profileForm, whatsappNumber: event.target.value })} /></label>
                  <button type="button" className="button button-light" disabled={loading || !profileForm.whatsappNumber} onClick={handleRequestProfileOtp}>Send verification OTP</button>
                  <label>WhatsApp OTP<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength="6" required value={profileForm.otp} onChange={(event) => setProfileForm({ ...profileForm, otp: event.target.value })} /></label>
                  <p className="muted">Changes are saved after you verify the mobile number with an OTP. Your username is used for your next sign-in.</p>
                  <p className="muted">Automatic rent reminders are sent at 9:00 AM India time on each active resident's monthly joining-date day.</p>
                  <div className="form-footer-actions">
                    <button type="button" className="button button-light" onClick={() => setProfileOpen(false)}>Cancel</button>
                    <button type="submit" className="button button-primary" disabled={loading}>{loading ? "Saving…" : "Save PG settings"}</button>
                  </div>
                </form>
              </section>
            </div>}

            {residents.length === 0 ? (
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
                            <button className="text-action" onClick={() => openRentPayments(resident)}>Rent history</button>
                            <button className="text-action" onClick={() => startEdit(resident)}>Edit</button>
                            {resident.status === "ACTIVE" && <button className="text-action text-danger" onClick={() => markVacated(resident)}>Vacate</button>}
                            <button className="text-action text-danger" onClick={() => permanentlyDeleteResident(resident)} disabled={loading}>Delete permanently</button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </article>

        </section>

        {rentPaymentResident && (
          <div className="dialog-backdrop" onClick={() => setRentPaymentResident(null)}>
            <section className="resident-dialog panel rent-payment-dialog" role="dialog" aria-modal="true" aria-labelledby="rent-payment-title" onClick={(event) => event.stopPropagation()}>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">MONTHLY RENT</p>
                  <h2 id="rent-payment-title">{rentPaymentResident.name} · Room {rentPaymentResident.roomNumber}</h2>
                  <p className="muted">Record rent paid by month. Confirmation is sent to the resident by WhatsApp.</p>
                </div>
                <button className="icon-button" onClick={() => setRentPaymentResident(null)} aria-label="Close rent history">×</button>
              </div>
              {error && <div className="alert alert-error dialog-alert" role="alert">{error}</div>}
              {notice && <div className="alert alert-success dialog-alert" role="status">{notice}</div>}
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
                  <label>Mobile number<input type="tel" name="mobileNumber" maxLength="20" required value={form.mobileNumber} onChange={updateForm} /></label>
                  <div className="form-row">
                    <label>Room number<input name="roomNumber" maxLength="30" required value={form.roomNumber} onChange={updateForm} /></label>
                    <label>Sharing size
                      <select name="sharing" required value={form.sharing} onChange={updateForm}>
                        <option value="">Select sharing</option>
                        <option value="1">Single sharing</option>
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

        {viewerResident && documentPreview && (
          <div className="dialog-backdrop" onClick={() => { setViewerResident(null); setDocumentPreview(null); }}>
            <section className="document-viewer panel" role="dialog" aria-modal="true" aria-labelledby="document-viewer-title" onClick={(event) => event.stopPropagation()}>
              <div className="panel-heading">
                <div>
                  <p className="eyebrow">PRIVATE RESIDENT FILE</p>
                  <h2 id="document-viewer-title">{viewerResident.name} · {documentPreview.title}</h2>
                  <p className="muted">Room {viewerResident.roomNumber}. Visible to the signed-in owner only.</p>
                </div>
                <button className="icon-button" onClick={() => { setViewerResident(null); setDocumentPreview(null); }} aria-label="Close file">×</button>
              </div>
              <div className="document-preview">
                {documentPreview.contentType.startsWith("image/") ? (
                  <img src={documentPreview.url} alt={documentPreview.title} />
                ) : (
                  <iframe title={documentPreview.title} src={documentPreview.url} />
                )}
              </div>
            </section>
          </div>
        )}

        <footer className="page-footer"><span>haven<span className="brand-period">.</span></span> Resident management, made simpler.</footer>
      </div>
    </main>
  );
}

export default App;
