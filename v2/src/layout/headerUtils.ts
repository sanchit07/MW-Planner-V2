/**
 * Returns user initials from first and last name, or fallback "G" for Guest.
 */
export function getUserInitials(
  firstName: string | undefined,
  lastName: string | undefined,
): string {
  if (firstName && lastName) {
    return firstName.charAt(0).toUpperCase() + lastName.charAt(0).toUpperCase();
  }
  return "G";
}
