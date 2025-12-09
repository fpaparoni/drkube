import React, { useState } from "react";

export default function DrKubeApp() {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [loading, setLoading] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!question.trim()) return;

    setLoading(true);
    setAnswer("");

    try {
        const url = `http://localhost:8091/issue?q=${encodeURIComponent(question)}`;

        const res = await fetch(url, {
          method: "GET",
        });
        
        const text = await res.text();
        setAnswer(text ?? "No response from DrKube.");
    } catch (err) {
        console.error(err);
        setAnswer("Error contacting DrKube.");
    } finally {
        setLoading(false);
    }
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        fontFamily: "sans-serif",
        background: "#f6f7f9",
        padding: "20px",
      }}
    >
      <div
        style={{
          display: "flex",
          gap: "40px",
          alignItems: "center",
          maxWidth: "900px",
          width: "100%",
        }}
      >
        {/* Left side image */}
        <div style={{ flexShrink: 0 }}>
          <img
            src="/drkube.jpg"
            alt="DrKube"
            style={{ width: "333px", height: "333px", objectFit: "contain" }}
          />
        </div>

        {/* Form & Answer */}
        <div
          style={{
            flexGrow: 1,
            background: "white",
            borderRadius: "16px",
            padding: "24px",
            boxShadow: "0 4px 16px rgba(0,0,0,0.08)",
          }}
        >
          <h2 style={{ marginTop: 0, marginBottom: "12px" }}>
            Ask DrKube about your cluster
          </h2>

          <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <textarea
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="Ask anything about your Kubernetes cluster..."
              disabled={loading}
              style={{
                width: "100%",
                height: "90px",
                padding: "12px",
                fontSize: "15px",
                borderRadius: "8px",
                border: "1px solid #ccc",
                resize: "vertical",
                boxSizing: "border-box",
              }}
            />

            <button
              type="submit"
              disabled={loading}
              style={{
                padding: "12px 16px",
                background: loading ? "#9ab6ff" : "#3b6cff",
                color: "white",
                border: "none",
                borderRadius: "8px",
                fontSize: "15px",
                cursor: loading ? "default" : "pointer",
                transition: "0.2s",
              }}
            >
              {loading ? "Consulting DrKube…" : "Ask DrKube"}
            </button>
          </form>

          {/* Answer box */}
          {answer && (
            <div
              style={{
                marginTop: "20px",
                padding: "16px",
                background: "#eef2ff",
                borderRadius: "8px",
                whiteSpace: "pre-wrap",
              }}
            >
              {answer}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
