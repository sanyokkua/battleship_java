import {CellDto, ShipDirection} from "../../../../logic/ApplicationTypes";

export type CellClickEventData = {
    cell: CellDto,
    isDelete: boolean,
    direction: ShipDirection | null;
};

export type Colors = "primary" | "secondary" | "success" | "warning" | "info" | "light" | "dark" | "danger";