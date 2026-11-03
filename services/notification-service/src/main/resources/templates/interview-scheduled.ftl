<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Interview Scheduled</title>
  <style>
    body { font-family: Arial, sans-serif; background:#f4f4f4; margin:0; padding:0; }
    .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px;
                 padding:32px; box-shadow:0 2px 8px rgba(0,0,0,0.1); }
    h1  { color:#8e44ad; }
    p   { color:#555; line-height:1.6; }
    .detail { background:#f8f4fb; border-left:4px solid #8e44ad; padding:12px 16px;
              border-radius:4px; margin:16px 0; }
    .footer { margin-top:32px; font-size:12px; color:#aaa; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Your Interview Has Been Scheduled</h1>
    <div class="detail">
      <p><strong>Interview ID:</strong> ${interviewId}<br/>
         <strong>Job ID:</strong>       ${jobId}<br/>
         <strong>Channel:</strong>      ${channel}<br/>
         <strong>Status:</strong>       ${status}</p>
    </div>
    <p>Please check your calendar and prepare accordingly. Good luck!</p>
    <div class="footer">
      &copy; Smart Hiring Assistant &mdash; automated notification
    </div>
  </div>
</body>
</html>
