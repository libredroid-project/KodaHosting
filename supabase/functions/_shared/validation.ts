import leoProfanity from "https://esm.sh/leo-profanity@1.7.0";

// Load English dictionary and add common German/International words manually
leoProfanity.loadDictionary('en');
leoProfanity.add(['hurensohn', 'wichser', 'fotze', 'schlampe', 'missgeburt', 'bastard', 'arschloch', 'fick', 'hitler', 'nazi']);

/**
 * Validates a hostname (subdomain) and returns an error code if invalid, or null if valid.
 */
export function validateHost(host: string): string | null {
  if (!host) return "missing_parameters";
  
  if (host.length < 3 || host.length > 63) {
    return "length_invalid";
  }
  
  const hostRegex = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
  if (!hostRegex.test(host)) {
    return "invalid_characters";
  }
  
  // check if any bad words are present in the host string
  if (leoProfanity.check(host)) {
    return "profanity_detected";
  }
  
  return null;
}
