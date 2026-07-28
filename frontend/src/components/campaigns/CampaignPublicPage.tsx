import React, { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { campaignsApi, donationsApi, donorsApi } from "../../api";

/**
 * Public-facing campaign page: progress bar toward the funding/disbursement
 * goal, an SLA countdown for rapid-response campaigns, and a donation
 * checkout box that hands off to Stripe Checkout (test mode).
 *
 * This is deliberately a *different* page from the admin CampaignsPage —
 * that one is an internal CRUD table; this is what a donor or the public
 * would actually see and act on.
 */
export const CampaignPublicPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [amount, setAmount] = useState("50");
  const [donorEmail, setDonorEmail] = useState("");
  const [donorFirstName, setDonorFirstName] = useState("");
  const [donorLastName, setDonorLastName] = useState("");
  const [isRecurring, setIsRecurring] = useState(false);
  const [checkoutError, setCheckoutError] = useState("");
  const [checkingOut, setCheckingOut] = useState(false);

  const { data: campaign, isLoading: campaignLoading } = useQuery({
    queryKey: ["campaigns", id],
    queryFn: () => campaignsApi.getById(id!),
    enabled: !!id,
  });

  const { data: progress } = useQuery({
    queryKey: ["campaigns", id, "progress"],
    queryFn: () => campaignsApi.getProgress(id!),
    enabled: !!id,
    // Live view — refetch periodically so the "X of Y paid" number moves
    // during an active bulk disbursement without the donor refreshing.
    refetchInterval: 15_000,
  });

  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, []);

  if (campaignLoading || !campaign) {
    return <div className="skeleton-table" />;
  }

  const fundingPercent = campaign.budgetUsd
    ? Math.min(100, Math.round((campaign.disbursedUsd / campaign.budgetUsd) * 1000) / 10)
    : 0;

  let slaCountdown: string | null = null;
  let slaBreached = false;
  if (campaign.triggeredAt && campaign.slaTargetHours) {
    const deadline =
      new Date(campaign.triggeredAt).getTime() + campaign.slaTargetHours * 3600_000;
    const remainingMs = deadline - now;
    slaBreached = remainingMs < 0;
    const abs = Math.abs(remainingMs);
    const hours = Math.floor(abs / 3600_000);
    const mins = Math.floor((abs % 3600_000) / 60_000);
    const secs = Math.floor((abs % 60_000) / 1000);
    slaCountdown = `${hours}h ${mins}m ${secs}s`;
  }

  const handleDonate = async () => {
    setCheckoutError("");
    if (!donorEmail || !donorFirstName || !donorLastName) {
      setCheckoutError("Please fill in your name and email first.");
      return;
    }
    setCheckingOut(true);
    try {
      // A public donation page wouldn't require a pre-existing donor account
      // in production (Stripe Checkout can collect the info directly) — here
      // we create/find a lightweight Donor record first since that's what
      // the donation history + impact endpoints key off of.
      const donor = await donorsApi.getOrCreate({
        firstName: donorFirstName,
        lastName: donorLastName,
        email: donorEmail,
        isRecurring,
      });

      const session = await donationsApi.createCheckoutSession({
        donorId: donor.id,
        campaignId: campaign.id,
        amountUsd: Number(amount),
        isRecurring,
        successUrl: `${window.location.origin}/campaigns/${campaign.id}/donate/success`,
        cancelUrl: window.location.href,
      });

      window.location.href = session.checkoutUrl;
    } catch (e: any) {
      setCheckoutError(
        e.response?.data?.detail ||
          "Could not start checkout. Is the Stripe test key configured on the backend?",
      );
    } finally {
      setCheckingOut(false);
    }
  };

  return (
    <div className="recipients-page campaign-public-page">
      <Link to="/campaigns" className="btn btn-ghost btn-sm" style={{ marginBottom: 16 }}>
        ← Back to campaigns
      </Link>

      <header className="page-header">
        <div>
          <h1 className="page-title">{campaign.name}</h1>
          <p className="page-subtitle">{campaign.description || "Cash transfer campaign"}</p>
        </div>
        <span className="badge badge--blue">{campaign.type.replace(/_/g, " ")}</span>
      </header>

      {/* Funding / disbursement progress */}
      <div className="card" style={{ padding: 20, marginBottom: 20 }}>
        <h3 style={{ marginBottom: 8 }}>Campaign Progress</h3>
        <div className="score-bar" style={{ height: 20, marginBottom: 8 }}>
          <div
            className="score-fill"
            style={{ width: `${fundingPercent}%`, background: "var(--color-green)" }}
          />
        </div>
        <p>
          ${campaign.disbursedUsd.toLocaleString()} disbursed of $
          {campaign.budgetUsd.toLocaleString()} budget ({fundingPercent}%)
        </p>

        {progress && progress.totalRecipients > 0 && (
          <p style={{ marginTop: 8 }}>
            <strong>
              {progress.completedCount} of {progress.totalRecipients}
            </strong>{" "}
            recipients paid ({progress.percentComplete}%)
            {progress.retryScheduledCount + progress.deadLetterCount > 0 && (
              <span style={{ color: "var(--color-yellow)" }}>
                {" "}
                · {progress.retryScheduledCount + progress.deadLetterCount} being retried/reviewed
              </span>
            )}
          </p>
        )}

        {slaCountdown && (
          <p
            style={{
              marginTop: 12,
              fontWeight: 600,
              color: slaBreached ? "var(--color-red)" : "var(--color-accent)",
            }}
          >
            {slaBreached
              ? `⚠ SLA window exceeded by ${slaCountdown} (target: ${campaign.slaTargetHours}h from trigger)`
              : `⏱ ${slaCountdown} remaining to reach full disbursement (${campaign.slaTargetHours}h SLA)`}
          </p>
        )}
      </div>

      {/* Donation checkout */}
      <div className="card" style={{ padding: 20 }}>
        <h3 style={{ marginBottom: 8 }}>Donate to this campaign</h3>
        {checkoutError && <p className="form-error">{checkoutError}</p>}
        <div className="form-grid">
          <label className="form-label">
            First Name
            <input
              className="form-input"
              value={donorFirstName}
              onChange={(e) => setDonorFirstName(e.target.value)}
            />
          </label>
          <label className="form-label">
            Last Name
            <input
              className="form-input"
              value={donorLastName}
              onChange={(e) => setDonorLastName(e.target.value)}
            />
          </label>
          <label className="form-label">
            Email
            <input
              className="form-input"
              type="email"
              value={donorEmail}
              onChange={(e) => setDonorEmail(e.target.value)}
            />
          </label>
          <label className="form-label">
            Amount (USD)
            <input
              className="form-input"
              type="number"
              min={1}
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          </label>
        </div>
        <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
          {[10, 25, 50, 100].map((preset) => (
            <button
              key={preset}
              className="btn btn-ghost btn-sm"
              onClick={() => setAmount(String(preset))}
            >
              ${preset}
            </button>
          ))}
        </div>
        <label className="form-label" style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
          <input
            type="checkbox"
            checked={isRecurring}
            onChange={(e) => setIsRecurring(e.target.checked)}
          />
          Make this a monthly recurring donation
        </label>
        <button
          className="btn btn-primary"
          onClick={handleDonate}
          disabled={checkingOut || !amount}
          style={{ marginTop: 12 }}
        >
          {checkingOut ? "Redirecting to checkout…" : `Donate $${amount || "0"}`}
        </button>
        <p className="form-hint" style={{ marginTop: 8 }}>
          Payment is processed securely by Stripe. Test mode: use card
          4242 4242 4242 4242, any future expiry, any CVC.
        </p>
      </div>
    </div>
  );
};
