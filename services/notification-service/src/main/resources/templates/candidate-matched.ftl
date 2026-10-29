<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Job Match Found</title>
  <style>
    body { font-family: Arial, sans-serif; background:#f4f4f4; margin:0; padding:0; }
    .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px;
                 padding:32px; box-shadow:0 2px 8px rgba(0,0,0,0.1); }
    h1  { color:#27ae60; }
    p   { color:#555; line-height:1.6; }
    .score { font-size:24px; font-weight:bold; color:#27ae60; }
    .footer { margin-top:32px; font-size:12px; color:#aaa; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Great news &mdash; you have been matched to a job!</h1>
    <p>Match ID: <strong>${matchId}</strong></p>
    <p>Job ID:   <strong>${jobId}</strong></p>
    <p>Match score: <span class="score">${matchScore?string["0.#"]}%</span></p>
    <p>Log in to review the details and take the next step.</p>
    <div class="footer">
      &copy; Smart Hiring Assistant &mdash; automated notification
    </div>
  </div>
</body>
</html>
