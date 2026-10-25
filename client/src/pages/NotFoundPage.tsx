import { Link } from "react-router";

export function NotFoundPage() {
  return (
    <div className="card" style={{ maxWidth: 520, margin: "40px auto", textAlign: "center", padding: 28 }}>
      <h1 style={{ fontSize: 22 }}>Page not found</h1>
      <p className="muted">The address may be mistyped, or the page has moved.</p>
      <Link to="/" className="btn">
        Go to your home page
      </Link>
    </div>
  );
}
