/*
 * Browser-side account form. The server still validates every request.
 * Student IDs stay as strings so leading zeros remain. The email suffix is fixed in HTML.
 * This file sends forms to the Servlet and reads JSON responses.
 * Tomcat keeps login state in an HttpOnly cookie; do not store passwords in browser storage.
 */
// Authentication UI only. Map behavior stays in app.js; account data lives on the server.
(() => {
  const byId = (id) => document.getElementById(id);
  const dialog = byId("auth-dialog");
  const form = byId("auth-form");
  let mode = "login",
    csrfToken = "",
    busy = false;
  window.SJSUAuth = {
    getCsrfToken: () => csrfToken
  };
  // Create one error area per field and link it to the input for screen readers.
  const inputs = [...form.querySelectorAll("input")];
  let lastStudentId = "";
  const touched = new Set();
  for (const input of inputs) {
    const error = document.createElement("small");
    error.id = `${input.id}-error`;
    error.className = "field-error";
    error.setAttribute("aria-live", "polite");
    input.closest(".auth-field").append(error);
    input.setAttribute("aria-describedby",
      `${input.getAttribute("aria-describedby") || ""} ${error.id}`.trim());
  }

  // Show a field error both visually and through the browser validity API.
  function fieldError(input, text) {
    input.setCustomValidity(text);
    input.setAttribute("aria-invalid", String(!!text));
    byId(`${input.id}-error`).textContent = text;
  }
  // Normalize name accents and spaces. Do not use this function on passwords.
  const normalizeName = (value) => value.normalize("NFC").replace(/\p{Zs}/gu, " ").trim().replace(/ +/g, " ");
  // Accept a username or a full SJSU address, then return only the part before the suffix.
  function emailUsername() {
    return byId("sjsu-email").value.trim().replace(/@sjsu\.edu$/i, "").toLowerCase();
  }
  // Check one active field. Login must not validate the hidden signup-only fields.
  function validate(input) {
    if (input.disabled || (mode === "login" && !["studentId", "password"].includes(input.name))) {
      fieldError(input, ""); return true;
    }
    let error = "";
    const value = input.value;
    switch (input.name) {
      case "studentId":
        if (!/^[0-9]{9}$/.test(value)) error = "Enter exactly 9 digits (0–9). Leading zeros are kept.";
        break;
      case "firstName":
      case "lastName": {
        const name = normalizeName(value);
        if (/[\p{Cc}\p{Cf}]/u.test(value) || name.length > 100 || !/^\p{L}[\p{L}\p{M}]*(?:[ '’\-]\p{L}[\p{L}\p{M}]*)*$/u.test(name))
          error = "Use 1–100 characters: letters, accents, spaces, hyphens or apostrophes.";
        break;
      }
      case "email": {
        const local = emailUsername();
        if (/[\p{Cc}\p{Cf}]/u.test(value) || local.length > 64 || !/^[a-z0-9_%+\-]+(?:\.[a-z0-9_%+\-]+)*$/.test(local))
          error = "Enter a valid SJSU email username. No spaces, other domains or empty dot segments.";
        break;
      }
      case "password":
        if (value.length < 8 || value.length > 128) error = "Use 8–128 characters. Passwords are never shortened.";
        else if (mode === "signup" && /^[\p{Z}\s]*$/u.test(value)) error = "Password cannot contain only spaces.";
        break;
      case "confirmPassword":
        if (!value) error = "Re-enter your password.";
        else if (value !== byId("auth-password").value) error = "Passwords do not match.";
        break;
    }
    fieldError(input, error);
    return !error;
  }
  const studentId = byId("student-id");
  // Reject invalid typing before it changes the ID. Some autofill tools skip this event.
  studentId.addEventListener("beforeinput", (event) => {
    if (event.isComposing || event.data == null || !event.inputType.startsWith("insert")) return;
    const next = studentId.value.slice(0, studentId.selectionStart) + event.data
      + studentId.value.slice(studentId.selectionEnd);
    if (!/^[0-9]{0,9}$/.test(next) && event.cancelable) {
      event.preventDefault();
      fieldError(studentId, "Only digits 0–9 are accepted, up to 9 digits.");
    }
  });
  // Reject the whole invalid paste instead of silently changing it to another student ID.
  studentId.addEventListener("paste", (event) => {
    const text = event.clipboardData.getData("text");
    const next = studentId.value.slice(0, studentId.selectionStart) + text
      + studentId.value.slice(studentId.selectionEnd);
    if (!/^[0-9]{0,9}$/.test(next)) {
      event.preventDefault();
      fieldError(studentId, "Paste a student ID containing only 9 digits. No letters or spaces.");
    }
  });
  // After the user leaves a field, validate it without moving keyboard focus.
  for (const input of inputs) {
    input.addEventListener("blur", () => {
      if (busy) return;
      if (input.name === "email" && /^[^@]+@sjsu\.edu$/i.test(input.value.trim()))
        input.value = emailUsername();
      touched.add(input.id); validate(input);
    });
  }

  // Show a form-wide status, such as a connection error or a pending request.
  function message(text, error = false) {
    byId("auth-message").textContent = text;
    byId("auth-message").classList.toggle("error", error);
  }

  // Switch between signup and login. Clear old values, touched fields and error messages.
  function setMode(next) {
    mode = next;
    const signup = mode === "signup";
    form.reset();
    lastStudentId = "";
    touched.clear();
    message("");
    for (const input of inputs) fieldError(input, "");
    byId("signup-fields").hidden = !signup;
    byId("signup-fields").disabled = !signup;
    byId("confirm-field").hidden = !signup;
    byId("confirm-password").disabled = !signup;
    byId("auth-title").textContent = signup
      ? "Make campus yours."
      : "Welcome back.";
    byId("auth-subtitle").textContent = signup
      ? "Create your SJSUClassrooms account."
      : "Sign in with your 9-digit student ID.";
    byId("auth-submit").textContent = signup ? "Create account" : "Sign in";
    byId("auth-switch-copy").textContent = signup
      ? "Already have an account?"
      : "New to SJSUClassrooms?";
    byId("auth-switch").textContent = signup ? "Sign in" : "Create an account";
    byId("auth-password").autocomplete = signup
      ? "new-password"
      : "current-password";
    byId("email-note").hidden = !signup;
  }

  // Use the server response to show the guest buttons or the signed-in user.
  function renderAccount(data) {
    csrfToken = data.csrfToken || "";
    byId("guest-actions").hidden = !!data.user;
    byId("member-actions").hidden = !data.user;
    byId("member-name").textContent = data.user
      ? `Hi, ${data.user.firstName}`
      : "";
    byId("member-name").title = data.user
      ? `Student ID: ${data.user.studentId}`
      : "";
  }

  // Send a same-origin request. Cookies travel automatically; timeout does not undo a server write.
  async function request(path, body) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    const options = { credentials: "same-origin", cache: "no-store", signal: controller.signal };
    if (body !== undefined) {
      options.method = "POST";
      options.headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "X-Requested-With": "SJSUClassrooms",
        "X-CSRF-Token": csrfToken,
      };
      options.body = body;
    }
    try {
      const response = await fetch(`api/auth/${path}`, options);
      let data;
      try { data = await response.json(); }
      catch { throw new Error("The server returned an unexpected response. Please try again later."); }
      if (!response.ok) {
        const error = new Error(data.error || "Please try again.");
        error.field = data.field;
        throw error;
      }
      return data;
    } catch (error) {
      if (error.name === "AbortError") throw new Error("The request timed out. If you were creating an account, try signing in before submitting again.");
      if (error instanceof TypeError) throw new Error("Unable to connect. Check your connection. If signup was interrupted, try signing in.");
      throw error;
    } finally { clearTimeout(timer); }
  }

  // Open the same dialog from either header account button.
  for (const button of document.querySelectorAll("[data-auth]")) {
    button.addEventListener("click", () => {
      setMode(button.dataset.auth);
      dialog.showModal();
      byId("student-id").focus();
    });
  }
  byId("auth-close").onclick = () => {
    if (!busy) dialog.close();
  };
  dialog.addEventListener("cancel", (event) => {
    if (busy) event.preventDefault();
  });
  dialog.addEventListener("close", () => {
    form.reset();
    message("");
  });
  byId("auth-switch").onclick = () => {
    if (!busy) {
      setMode(mode === "login" ? "signup" : "login");
      byId("student-id").focus();
    }
  };
  // Fallback for autofill and other input paths. Re-check touched fields as the user fixes them.
  form.addEventListener("input", (event) => {
    const input = event.target;
    if (input === studentId) {
      if (!/^[0-9]{0,9}$/.test(input.value)) {
        input.value = lastStudentId;
        fieldError(input, "Only digits 0–9 are accepted, up to 9 digits.");
        return;
      }
      lastStudentId = input.value;
    }
    if (input.id === "sjsu-email" && /^[^@]+@sjsu\.edu$/i.test(input.value.trim()))
      input.value = emailUsername();
    if (touched.has(input.id)) validate(input);
    else fieldError(input, "");
    if (input.id === "auth-password" && touched.has("confirm-password")) validate(byId("confirm-password"));
    message("");
  });

  // Block a second submit and keep field values stable until the request finishes.
  function setBusy(value) {
    busy = value;
    form.setAttribute("aria-busy", String(value));
    for (const id of ["auth-submit", "auth-switch", "auth-close"])
      byId(id).disabled = value;
    for (const input of inputs) input.readOnly = value;
  }

  // Validate all fields first. Add the fixed email suffix only when building the request body.
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy) return;
    const invalid = inputs.filter((input) => { touched.add(input.id); return !validate(input); });
    if (invalid.length) {
      message("Please correct the highlighted fields.", true);
      invalid[0].focus(); return;
    }
    const body = new URLSearchParams(new FormData(form));
    if (mode === "signup") {
      body.set("email", `${emailUsername()}@sjsu.edu`);
      body.set("firstName", normalizeName(byId("first-name").value));
      body.set("lastName", normalizeName(byId("last-name").value));
    }
    setBusy(true);
    message(mode === "signup" ? "Creating your account…" : "Signing in…");
    try {
      const data = await request(mode, body);
      renderAccount(data);
      dialog.close();
      byId("account-status").textContent =
        mode === "signup"
          ? "Account created. You are signed in."
          : "You are signed in.";
      byId("logout").focus();
    } catch (error) {
      const input = inputs.find((input) => input.name === error.field);
      if (input) { fieldError(input, error.message); input.focus(); }
      message(
        error instanceof TypeError
          ? "Unable to connect. Please try again."
          : error.message,
        true,
      );
    } finally {
      setBusy(false);
    }
  });

  // Ask the server to end the session, then update the header.
  byId("logout").onclick = async () => {
    byId("logout").disabled = true;
    try {
      renderAccount(await request("logout", new URLSearchParams()));
      byId("account-status").textContent = "You are signed out.";
      byId("sign-in").focus();
    } catch (error) {
      byId("account-status").textContent = error.message;
    } finally {
      byId("logout").disabled = false;
    }
  };
  // Check /me on page load and window focus, because the server session may have expired.
  async function refreshSession() {
    try {
      renderAccount(await request("me"));
    } catch {
      byId("account-status").textContent =
        "Account connection unavailable. You can still explore campus.";
    }
  }
  window.addEventListener("focus", () => {
    if (!busy) refreshSession();
  });
  refreshSession();
})();
