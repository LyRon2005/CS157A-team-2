(() => {
  let selectedBuilding = null;

  const form = document.getElementById("review-form");
  const text = document.getElementById("review-text");
  const message = document.getElementById("review-message");

  if (!form) return;

  // Remember which building the user selected.
  window.addEventListener("building-selected", (event) => {
    selectedBuilding = event.detail;
    message.textContent = `Writing a review for ${event.detail.name}`;
    text.value = "";
  });

    form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const building = selectedBuilding || window.selectedBuilding;

    if (!building) {
        message.textContent = "Please select a building first.";
        return;
    }

    const writtenReview = text.value.trim();

    if (!writtenReview) {
        message.textContent = "Please write a review.";
        return;
    }

    const body = new URLSearchParams();
    body.set("buildingName", building.name);
    body.set("writtenReview", writtenReview);

    try {
      const response = await fetch("api/reviews", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "X-Requested-With": "SJSUClassrooms",
          "X-CSRF-Token": window.SJSUAuth.getCsrfToken()
        },
        body: body
      });

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.error || "Could not submit review.");
      }

      message.textContent = "Review submitted successfully.";
      text.value = "";

    } catch (error) {
      message.textContent = error.message;
    }
  });
})();