import { Component, type ErrorInfo, type ReactNode } from "react";

interface State {
  error: Error | null;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("Unhandled UI error", error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 32 }}>
          <div className="notice bad" role="alert">
            <strong>This page failed to load.</strong> Reload the page; if the problem continues, contact support.
          </div>
          <button type="button" className="btn" style={{ marginTop: 12 }} onClick={() => window.location.reload()}>
            Reload
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
