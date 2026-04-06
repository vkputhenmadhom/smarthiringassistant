import React, { useState } from 'react';
import {
  Card, Input, Row, Col, Tag, Button, Spin, Empty,
  Typography, message, Alert, Tooltip,
} from 'antd';
import {
  SearchOutlined, EnvironmentOutlined, BankOutlined,
  LinkOutlined, BuildOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation } from '@apollo/client';
import { useNavigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { RootState } from '../../store';
import { Job, GOVT_JOBS_QUERY, START_SCREENING_MUTATION, GraphQLPage } from '../../graphql';

const { Title, Text } = Typography;

type StartScreeningResponse = {
  startScreening: {
    id: string;
    candidateId: string;
    jobId: string;
    currentStage: string;
    status: string;
    decision: string;
  };
};

/** Maps source tag to a human-readable portal label */
const sourceLabel = (source?: string): string => {
  switch (source) {
    case 'JSEARCH_IN': return 'LinkedIn / Indeed India';
    case 'ADZUNA_IN':  return 'Adzuna India';
    default:           return source ?? 'External';
  }
};

const GovtJobsPage: React.FC = () => {
  const navigate = useNavigate();
  const user     = useSelector((s: RootState) => s.auth.user);

  const [search,     setSearch]     = useState('');
  const [searchVar,  setSearchVar]  = useState('');
  const [applied,    setApplied]    = useState<Set<string>>(new Set());
  const [applying,   setApplying]   = useState<Set<string>>(new Set());
  const [sessionByJob, setSessionByJob] = useState<Record<string, string>>({});

  const { data, loading } = useQuery<{ jobs: GraphQLPage<Job> }>(GOVT_JOBS_QUERY, {
    variables: { page: 0, size: 30, search: searchVar || undefined },
    fetchPolicy: 'network-only',
  });

  const [startScreening] = useMutation<StartScreeningResponse>(START_SCREENING_MUTATION);

  const jobs          = data?.jobs?.content ?? [];
  const totalElements = data?.jobs?.totalElements ?? 0;

  const handleSearch = () => setSearchVar(search);

  const handleApply = async (jobId: string, externalUrl?: string) => {
    // If the job has an external URL, open it in a new tab first
    if (externalUrl) window.open(externalUrl, '_blank', 'noopener');

    if (!user?.id) {
      message.error('Unable to identify candidate. Please sign in again.');
      return;
    }
    setApplying(prev => new Set([...prev, jobId]));
    try {
      const { data: mutData } = await startScreening({
        variables: { candidateId: user.id, jobId },
      });
      const sessionId = mutData?.startScreening?.id;
      if (!sessionId) throw new Error('Screening session was not created.');
      setApplied(prev => new Set([...prev, jobId]));
      setSessionByJob(prev => ({ ...prev, [jobId]: sessionId }));
      message.success('Application recorded. Starting interview prep.');
      navigate(`/interview?sessionId=${sessionId}`);
    } catch (err) {
      message.error(err instanceof Error ? err.message : 'Failed to submit application');
    } finally {
      setApplying(prev => { const n = new Set(prev); n.delete(jobId); return n; });
    }
  };

  return (
    <div>
      {/* Page header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
        <span style={{ fontSize: 28 }}>🇮🇳</span>
        <Title level={4} style={{ margin: 0 }}>
          India Government &amp; PSU Jobs ({totalElements})
        </Title>
      </div>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16, fontSize: 13 }}
        message={
          <>
            Jobs are sourced from <strong>LinkedIn India</strong>, <strong>Indeed.co.in</strong> and{' '}
            <strong>Adzuna India</strong> — which carry listings from DRDO, ISRO, NTPC, BHEL,
            Indian Railways, IBPS, SBI and other PSU/central-government employers.
            A sync must be triggered (or scheduled) for jobs to appear here.
          </>
        }
      />

      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        <Input
          placeholder="Search by title, organisation, skills..."
          value={search}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearch(e.target.value)}
          onPressEnter={handleSearch}
          prefix={<SearchOutlined />}
          size="large"
          style={{ maxWidth: 440, flex: '1 1 280px' }}
        />
        <Button size="large" onClick={handleSearch}>Search</Button>
      </div>

      {loading && <Spin size="large" style={{ display: 'block', margin: '3rem auto' }} />}

      {!loading && jobs.length === 0 && (
        <Empty
          description={
            <span>
              No India government jobs found.{' '}
              <Text type="secondary" style={{ fontSize: 12 }}>
                Trigger a sync via <code>POST /api/jobs/sync/trigger</code> with{' '}
                <code>JOB_SYNC_INDIA_ENABLED=true</code>.
              </Text>
            </span>
          }
        />
      )}

      <Row gutter={[16, 16]}>
        {jobs.map((job: Job) => (
          <Col xs={24} md={12} key={job.id}>
            <Card
              hoverable
              title={
                <span>
                  <BankOutlined style={{ marginRight: 6, color: '#3f51b5' }} />
                  {job.title}
                </span>
              }
              extra={
                <Tag color="green" style={{ fontWeight: 600 }}>
                  {job.status}
                </Tag>
              }
              actions={[
                job.externalUrl
                  ? (
                    <Tooltip title="Opens original posting in a new tab">
                      <Button
                        icon={<LinkOutlined />}
                        loading={applying.has(job.id)}
                        onClick={() => handleApply(job.id, job.externalUrl)}
                        disabled={applied.has(job.id)}
                      >
                        {applied.has(job.id) ? 'Applied' : 'Apply / View'}
                      </Button>
                    </Tooltip>
                  )
                  : (
                    <Button
                      loading={applying.has(job.id)}
                      onClick={() => handleApply(job.id)}
                      disabled={applied.has(job.id)}
                    >
                      {applied.has(job.id)
                        ? <span onClick={() => navigate(`/interview?sessionId=${sessionByJob[job.id]}`)}>Continue Prep</span>
                        : 'Apply Now'}
                    </Button>
                  ),
              ]}
            >
              {/* Organisation + location */}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 8, color: '#555', fontSize: 13 }}>
                {job.companyName && (
                  <span><BuildOutlined /> <strong>{job.companyName}</strong></span>
                )}
                <span><EnvironmentOutlined /> {job.location ?? 'India'}</span>
                <span>· {job.type.replace('_', ' ')}</span>
                {job.salaryMin && (
                  <span>
                    ₹ {(job.salaryMin / 100000).toFixed(1)}L – {((job.salaryMax ?? 0) / 100000).toFixed(1)}L
                  </span>
                )}
              </div>

              {/* Skills */}
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginBottom: 8 }}>
                {job.skills.slice(0, 6).map(s => (
                  <Tag key={s} color="orange">{s}</Tag>
                ))}
                {job.skills.length > 6 && <Tag>+{job.skills.length - 6}</Tag>}
              </div>

              {/* Source badge */}
              <Text type="secondary" style={{ fontSize: 11 }}>
                Via {sourceLabel(job.source)}
              </Text>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
};

export default GovtJobsPage;

