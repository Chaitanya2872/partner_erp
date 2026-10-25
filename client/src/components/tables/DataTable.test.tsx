import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { DataTable, type ColumnDef } from "./DataTable";
import { pageWindow } from "@/components/navigation/pageWindow";
import { ApiRequestError } from "@/services/api";
import { EmptyState } from "@/components/feedback/States";

interface Row {
  id: string;
  name: string;
  amount: number;
}

const columns: ColumnDef<Row>[] = [
  { id: "name", header: "Name", sortKey: "name", cell: (r) => r.name },
  { id: "amount", header: "Amount", numeric: true, sortKey: "amount", cell: (r) => r.amount },
  { id: "notes", header: "Notes", defaultHidden: true, cell: () => "note" },
];

const rows: Row[] = [
  { id: "1", name: "Alpha", amount: 10 },
  { id: "2", name: "Beta", amount: 20 },
];

describe("DataTable pagination", () => {
  it("shows the record range and moves between pages", async () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        caption="Things"
        itemLabel="things"
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        page={{ page: 1, size: 20, totalElements: 45, totalPages: 3 }}
        onPageChange={onPageChange}
      />,
    );
    expect(screen.getByText(/Showing/)).toHaveTextContent("Showing 21–40 of 45 things");
    expect(screen.getByRole("button", { name: "Page 2" })).toHaveAttribute("aria-current", "page");
    await userEvent.click(screen.getByRole("button", { name: "Next page" }));
    expect(onPageChange).toHaveBeenLastCalledWith(2);
    await userEvent.click(screen.getByRole("button", { name: "Previous page" }));
    expect(onPageChange).toHaveBeenLastCalledWith(0);
    await userEvent.click(screen.getByRole("button", { name: "Page 3" }));
    expect(onPageChange).toHaveBeenLastCalledWith(2);
  });

  it("disables navigation at the edges and offers page sizes", async () => {
    const onPageSizeChange = vi.fn();
    render(
      <DataTable
        caption="Things"
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        page={{ page: 0, size: 20, totalElements: 30, totalPages: 2 }}
        onPageChange={() => undefined}
        pageSizeOptions={[20, 50]}
        onPageSizeChange={onPageSizeChange}
      />,
    );
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled();
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Rows per page" }), "50");
    expect(onPageSizeChange).toHaveBeenCalledWith(50);
  });

  it("windows long page lists", () => {
    expect(pageWindow(0, 5)).toEqual([0, 1, 2, 3, 4]);
    expect(pageWindow(10, 20)).toEqual([0, null, 9, 10, 11, null, 19]);
  });
});

describe("DataTable states", () => {
  it("shows skeleton rows and announces loading", () => {
    render(<DataTable caption="Things" itemLabel="things" columns={columns} rows={undefined} rowKey={(r) => r.id} loading />);
    expect(screen.getByRole("table")).toHaveAttribute("aria-busy", "true");
    expect(screen.getByRole("status")).toHaveTextContent("Loading things…");
    expect(screen.queryByText("Alpha")).not.toBeInTheDocument();
  });

  it("shows the empty state when there are no rows", () => {
    render(<DataTable caption="Things" columns={columns} rows={[]} rowKey={(r) => r.id} empty={<EmptyState title="Nothing here" />} />);
    expect(screen.getByText("Nothing here")).toBeInTheDocument();
  });

  it("shows a described error with retry", async () => {
    const onRetry = vi.fn();
    render(<DataTable caption="Things" columns={columns} rows={undefined} rowKey={(r) => r.id} error={new ApiRequestError(500, "INTERNAL", "boom")} onRetry={onRetry} />);
    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("The server had a problem");
    expect(alert).not.toHaveTextContent("boom");
    await userEvent.click(within(alert).getByRole("button", { name: /Retry/ }));
    expect(onRetry).toHaveBeenCalled();
  });
});

describe("DataTable sorting, columns and actions", () => {
  it("requests server-side sorting from sortable headers", async () => {
    const onSortChange = vi.fn();
    render(<DataTable caption="Things" columns={columns} rows={rows} rowKey={(r) => r.id} sort={{ key: "name", direction: "asc" }} onSortChange={onSortChange} />);
    expect(screen.getByRole("columnheader", { name: /Name/ })).toHaveAttribute("aria-sort", "ascending");
    await userEvent.click(screen.getByRole("button", { name: /Name/ }));
    expect(onSortChange).toHaveBeenCalledWith({ key: "name", direction: "desc" });
    await userEvent.click(screen.getByRole("button", { name: /Amount/ }));
    expect(onSortChange).toHaveBeenCalledWith({ key: "amount", direction: "asc" });
  });

  it("hides and shows columns from the column menu", async () => {
    render(<DataTable caption="Things" columns={columns} rows={rows} rowKey={(r) => r.id} />);
    expect(screen.queryByRole("columnheader", { name: "Notes" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Columns/ }));
    await userEvent.click(await screen.findByRole("menuitemcheckbox", { name: "Notes" }));
    await userEvent.click(screen.getByRole("menuitemcheckbox", { name: "Amount" }));
    // The menu stays open between toggles; close it so the table is exposed again.
    await userEvent.keyboard("{Escape}");
    expect(screen.getByRole("columnheader", { name: "Notes" })).toBeInTheDocument();
    expect(screen.queryByRole("columnheader", { name: /Amount/ })).not.toBeInTheDocument();
  });

  it("renders only the row actions the caller provides", () => {
    render(
      <DataTable
        caption="Things"
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        rowActions={(r) => (r.name === "Alpha" ? <button type="button">Edit {r.name}</button> : null)}
      />,
    );
    expect(screen.getByRole("button", { name: "Edit Alpha" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit Beta" })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: /Things/ })).toHaveAttribute("tabindex", "0");
  });
});
