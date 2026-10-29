import React, { useMemo, useState } from 'react';
import { Card, Button, Input, Alert, Tag, Typography, Divider, List, Spin } from 'antd';
import { useMutation, useQuery } from '@apollo/client';
import { SendOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import {
  SCREENING_SESSION_QUERY,
  SUBMIT_SCREENING_RESPONSE_MUTATION,
  ScreeningSession,
  StageResult,
} from '../../graphql';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;


type ScreeningSessionResponse = {
  screeningSession: ScreeningSession;
};

type SubmitScreeningResponse = {
  submitScreeningResponse: ScreeningSession;
};

const stagePrompts: Record<string, string> = {
  initial: 'Introduce your experience, motivations, and role-fit clearly.',
  technical: 'Explain implementation choices, trade-offs, and measurable outcomes.',
  behavioral: 'Use STAR format with concrete examples and impact.',
};

const InterviewPrepPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const sessionId = searchParams.get('sessionId') ?? '';
  const [sessionInput, setSessionInput] = useState(sessionId);
  const [response, setResponse] = useState('');

  const { data, loading, error, refetch } = useQuery<ScreeningSessionResponse>(SCREENING_SESSION_QUERY, {
    variables: { id: sessionId },
    skip: !sessionId,
    fetchPolicy: 'network-only',
  });

  const [submitResponse, { loading: submitting, error: submitError }] =
    useMutation<SubmitScreeningResponse>(SUBMIT_SCREENING_RESPONSE_MUTATION);

  const session = data?.screeningSession;
  const currentStage = session?.currentStage ?? 'initial';
  const latestStageResult = useMemo(
    () => session?.stageResults?.find((result: StageResult) => result.stage === currentStage),
    [session, currentStage]
  );

  const loadSession = () => {
    const trimmed = sessionInput.trim();
    if (!trimmed) {
      return;
    }
    setSearchParams({ sessionId: trimmed });
  };

  const handleSubmit = async () => {
    if (!sessionId || !response.trim() || !session?.currentStage) {
      return;
    }

    await submitResponse({
      variables: {
        sessionId,
        stage: session.currentStage,
        response: response.trim(),
      },
    });

    setResponse('');
    await refetch();
  };

  return (
    <div style={{ maxWidth: 900, width: '100%' }}>
      <Title level={4}>Interview Preparation</Title>
      <Paragraph type="secondary">
        Submit real screening responses through GraphQL and track stage-by-stage decisions.
      </Paragraph>

      <Card title="Load Screening Session" style={{ marginBottom: 16 }}>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          <Input
            placeholder="Paste screening session ID"
            value={sessionInput}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSessionInput(e.target.value)}
            onPressEnter={loadSession}
            style={{ maxWidth: 420, flex: '1 1 280px' }}
          />
          <Button type="primary" onClick={loadSession}>Load Session</Button>
        </div>
      </Card>

      {!sessionId && (
        <Alert
          type="info"
          message="No active session selected"
          description="Apply from the Jobs page to auto-open interview prep with a valid session."
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {loading && <Spin size="large" style={{ display: 'block', margin: '3rem auto' }} />}
      {error && <Alert type="error" message={error.message} showIcon style={{ marginBottom: 16 }} />}

      {session && (
        <>
          <Card title="Session Overview" style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <Tag color="blue">Session: {session.id}</Tag>
              <Tag color="purple">Stage: {session.currentStage}</Tag>
              <Tag color={session.status === 'COMPLETED' ? 'green' : 'orange'}>Status: {session.status}</Tag>
              <Tag color={session.decision === 'PASS' ? 'green' : session.decision === 'FAIL' ? 'red' : 'default'}>
                Decision: {session.decision}
              </Tag>
            </div>
            <Paragraph style={{ marginTop: 12, marginBottom: 0 }}>
              <Text strong>Prompt: </Text>
              {stagePrompts[currentStage] ?? 'Describe your approach clearly and include concrete outcomes.'}
            </Paragraph>
          </Card>

          <Card title={`Submit ${currentStage} response`}>
            <TextArea
              rows={6}
              value={response}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setResponse(e.target.value)}
              placeholder="Write your response for the current screening stage"
              style={{ marginBottom: 12 }}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={submitting}
              disabled={!response.trim() || session.status === 'COMPLETED'}
              onClick={handleSubmit}
            >
              Submit Response
            </Button>
            {submitError && (
              <Alert
                type="error"
                message={submitError.message}
                showIcon
                style={{ marginTop: 12 }}
              />
            )}
          </Card>

          {latestStageResult && (
            <Card title="Latest Stage Feedback" style={{ marginTop: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
                <Tag color={latestStageResult.passed ? 'green' : 'red'}>
                  {latestStageResult.passed ? 'PASS' : 'FAIL'}
                </Tag>
                <Text strong>Score: {latestStageResult.score.toFixed(1)}/100</Text>
              </div>
              {latestStageResult.reasons?.length ? (
                <List
                  size="small"
                  dataSource={latestStageResult.reasons}
                  renderItem={(reason: string) => <List.Item>{reason}</List.Item>}
                />
              ) : (
                <Text type="secondary">No reasoning details returned for this stage yet.</Text>
              )}
            </Card>
          )}

          <Card title="Session Progress" style={{ marginTop: 16 }}>
            <List
              dataSource={session.stageResults ?? []}
              locale={{ emptyText: 'No stage results yet.' }}
              renderItem={(result: StageResult) => (
                <List.Item>
                  <div style={{ width: '100%' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                      <Text strong>{result.stage}</Text>
                      <Text>{result.score.toFixed(1)}/100</Text>
                    </div>
                    <Tag color={result.passed ? 'green' : 'red'} style={{ marginTop: 6 }}>
                      {result.passed ? 'PASS' : 'FAIL'}
                    </Tag>
                  </div>
                </List.Item>
              )}
            />

            {session.finalScore != null && (
              <>
                <Divider />
                <Text strong>Final Score: {session.finalScore.toFixed(1)}/100</Text>
                {!!session.failureReasons?.length && (
                  <List
                    size="small"
                    style={{ marginTop: 8 }}
                    dataSource={session.failureReasons}
                    renderItem={(reason: string) => <List.Item>{reason}</List.Item>}
                  />
                )}
              </>
            )}
          </Card>
        </>
      )}
    </div>
  );
};

export default InterviewPrepPage;
