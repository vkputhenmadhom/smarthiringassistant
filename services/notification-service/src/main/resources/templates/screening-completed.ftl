<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Screening Result</title>
  <style>
    body { font-family: Arial, sans-serif; background:#f4f4f4; margin:0; padding:0; }
    .container { max-width:600px; margin:40px auto; background:#fff; border-radius:8px;
                 padding:32px; box-shadow:0 2px 8px rgba(0,0,0,0.1); }
    h1   { color:#2c3e50; }
    p    { color:#555; line-height:1.6; }
    .pass { color:#27ae60; font-weight:bold; }
    .fail { color:#e74c3c; font-weight:bold; }
    .footer { margin-top:32px; font-size:12px; color:#aaa; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Screening Complete</h1>
    <p>Session ID: <strong>${sessionId}</strong></p>
    <p>Decision:
      <#if decision == "PASS">
        <span class="pass">PASS &mdash; Congratulations!</span>
      <#else>
        <span class="fail">FAIL</span>
      </#if>
    </p>
    <p>Final score: <strong>${finalScore?string["0.#"]}</strong></p>
    <#if failureReasons?has_content>
      <p>Reasons:</p>
      <ul>
        <#list failureReasons as reason>
          <li>${reason}</li>
        </#list>
      </ul>
    </#if>
    <div class="footer">
      &copy; Smart Hiring Assistant &mdash; automated notification
    </div>
  </div>
</body>
</html>
