// src/utils/errors.ts
import { AxiosError } from "axios";
import type { ApiError } from "../types";

export function extractErrorMessage(err: unknown): string {
  if (err instanceof AxiosError) {
    const data = err.response?.data as ApiError | undefined;

    if (data?.fieldErrors) {
      const messages = Object.entries(data.fieldErrors)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join(", ");
      return messages || "Validation failed.";
    }

    if (data?.detail) return data.detail;

    switch (err.response?.status) {
      case 400:
        return "Invalid request. Please check the form and try again.";
      case 401:
        return "Your session has expired. Please log in again.";
      case 403:
        return "You don't have permission to perform this action.";
      case 404:
        return "The requested resource was not found.";
      case 409:
        return "This action conflicts with the current state of the resource.";
      case 422:
        return "Unable to process this request due to a business rule violation.";
      case 500:
        return "Something went wrong on our end. Please try again shortly.";
      case 503:
        return "The service is temporarily unavailable. Please try again later.";
      default:
        if (err.code === "ECONNABORTED")
          return "The request timed out. Please try again.";
        if (!err.response)
          return "Network error — please check your connection.";
        return `Unexpected error (${err.response?.status}).`;
    }
  }

  if (err instanceof Error) return err.message;
  return "An unexpected error occurred.";
}
