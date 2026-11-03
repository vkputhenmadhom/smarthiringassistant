import React, { useEffect, useState } from 'react';
import { Alert, Card, Input, Row, Col, Tag, Button, Spin, Empty, Typography, Tabs, Pagination, message } from 'antd';
import { SearchOutlined, EnvironmentOutlined, BuildOutlined, LinkOutlined, ReloadOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { useDispatch, useSelector } from 'react-redux';
import { useMutation, useQuery } from '@apollo/client';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchJobs } from '../../store/jobsSlice';
import { RootState, AppDispatch } from '../../store';
import { Job, JobFeed, JobMatch, JOB_FEEDS_QUERY, MY_MATCHES_QUERY, START_SCREENING_MUTATION } from '../../graphql';

const { Title, Text } = Typography;

const PAGE_SIZE = 20;

/** The tab with the candidate's matches; the other tabs are the configured job feeds */
const RECOMMENDED = 'recommended';

/** "Lead · 10+ yrs", "Senior · 5–12 yrs", "5+ yrs", or null when nothing is known */
const seniorityText = (job: Job): string | null => {
  const min = job.minExperienceYears;
  const max = job.maxExperienceYears;
  const years = min == null && max == null ? null
    : max == null ? `${min}+ yrs`
    : min === max || min == null ? `${max} yrs` : `${min}–${max} yrs`;
  if (job.seniority && years) return `${job.seniority} · ${years}`;
  return job.seniority ?? years;
};

/** "Posted today", "Posted 3 days ago" */
const postedText = (postedAt?: string): string | null => {
  if (!postedAt) return null;
  const days = Math.floor((Date.now() - new Date(postedAt).getTime()) / 86_400_000);
  if (Number.isNaN(days) || days < 0) return null;
  return days === 0 ? 'Posted today' : days === 1 ? 'Posted yesterday' : `Posted ${days} days ago`;
};

const scoreColor = (score: number) => (score >= 0.75 ? 'green' : score >= 0.6 ? 'blue' : 'gold');

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

/** Where an imported job came from, for the card footer */
const sourceLabel = (source?: string): string | null => {
  if (!source || source === 'INTERNAL') return null;
  if (source.startsWith('ADZUNA')) return 'Adzuna';
  if (source.startsWith('JSEARCH')) return 'LinkedIn / Indeed via JSearch';
  return source;
};

/** INR as lakh per year (₹ 6.0L – 9.5L); other currencies in thousands (USD 120k – 150k) */
const salaryText = (job: Job): string | null => {
  if (!job.salaryMin) return null;
  const max = job.salaryMax ?? job.salaryMin;
  if (!job.salaryCurrency || job.salaryCurrency === 'INR') {
    return `₹ ${(job.salaryMin / 100000).toFixed(1)}L – ${(max / 100000).toFixed(1)}L`;
  }
  return `${job.salaryCurrency} ${(job.salaryMin / 1000).toFixed(0)}k – ${(max / 1000).toFixed(0)}k`;
};

const JobsPage: React.FC = () => {
  const dispatch = useDispatch<AppDispatch>();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  // Tabs are the feeds configured in job-analyzer's job-feeds.yml
  const { data: feedData, loading: feedsLoading, error: feedsError } = useQuery<{ jobFeeds: JobFeed[] }>(JOB_FEEDS_QUERY);
  const feeds = feedData?.jobFeeds ?? [];
  const user = useSelector((s: RootState) => s.auth.user);
  const authToken = useSelector((s: RootState) => s.auth.token);
  // Jobs matched to the candidate's resume (the matcher keeps the best 20 per resume)
  const { data: matchData, loading: matchesLoading, error: matchesError, refetch: refetchMatches } = useQuery<{ matchesForCandidate: { totalElements: number; content: JobMatch[] } }>(
    MY_MATCHES_QUERY, { variables: { candidateId: user?.id, size: PAGE_SIZE }, skip: !user?.id, fetchPolicy: 'network-only' });
  // A job that has closed since the match is no longer worth showing
  const matches = (matchData?.matchesForCandidate?.content ?? []).filter(m => m.job && m.job.status === 'OPEN');
  const [refreshing, setRefreshing] = useState(false);

  /** Matches the latest resume against today's jobs, without a new upload */
  const refreshMatches = async () => {
    const token = authToken ?? localStorage.getItem('sha_token');
    setRefreshing(true);
    try {
      const response = await fetch('/api/resumes/candidates/me/rematch', {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (response.status === 404) {
        message.info('Upload your resume first; matches come from it.');
        return;
      }
      if (!response.ok) throw new Error(`Could not refresh your matches (${response.status})`);
      // Matching runs in the background; it takes a few seconds
      await new Promise(resolve => setTimeout(resolve, 4000));
      await refetchMatches();
      message.success('Matches refreshed with the latest jobs.');
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Could not refresh your matches');
    } finally {
      setRefreshing(false);
    }
  };
  const matchByJob = new Map(matches.map(m => [m.job!.id, m]));
  const requested = params.get('feed');
  const recommended = requested === RECOMMENDED || (!requested && !!user?.id);
  const feed = feeds.find(f => f.key === requested) ?? feeds[0];
  const { items, loading, totalElements, page, error } = useSelector((s: RootState) => s.jobs);
  const [search, setSearch]   = useState('');
  const [query, setQuery]     = useState('');
  const [applied, setApplied] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState<Set<string>>(new Set());
  const [sessionByJob, setSessionByJob] = useState<Record<string, string>>({});

  const [startScreening] = useMutation<StartScreeningResponse>(START_SCREENING_MUTATION);

  const load = (pageIndex: number) => {
    if (feed && !recommended) dispatch(fetchJobs({ source: feed.key, search: query || undefined, page: pageIndex, size: PAGE_SIZE }));
  };

  useEffect(() => { load(0); }, [dispatch, feed?.key, query, recommended]); // eslint-disable-line react-hooks/exhaustive-deps

  const changeFeed = (key: string) => setParams({ feed: key }, { replace: true });

  const handleApply = async (jobId: string) => {
    if (!user?.id) {
      message.error('Unable to identify candidate. Please sign in again.');
      return;
    }

    setApplying((prev: Set<string>) => new Set([...prev, jobId]));
    try {
      const { data } = await startScreening({
        variables: {
          candidateId: user.id,
          jobId,
        },
      });

      const sessionId = data?.startScreening?.id;
      if (!sessionId) {
        throw new Error('Screening session was not created.');
      }

      setApplied((prev: Set<string>) => new Set([...prev, jobId]));
      setSessionByJob((prev: Record<string, string>) => ({ ...prev, [jobId]: sessionId }));
      message.success('Application submitted. Continuing to interview prep.');
      navigate(`/interview?sessionId=${sessionId}`);
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'Failed to submit application';
      message.error(reason);
    } finally {
      setApplying((prev: Set<string>) => {
        const next = new Set(prev);
        next.delete(jobId);
        return next;
      });
    }
  };

  /** One job card; with a match it shows the match score and which of the candidate's skills fit */
  const renderJob = (job: Job, match?: JobMatch) => {
    const salary = salaryText(job);
    const via = sourceLabel(job.source);
    const level = seniorityText(job);
    const posted = postedText(job.postedAt);
    return (
      <Col xs={24} md={12} key={job.id}>
        <Card
          hoverable
          title={job.title}
          extra={match
            ? <Tag color={scoreColor(match.score)}>{Math.round(match.score * 100)}% match</Tag>
            : <Tag color={job.status === 'OPEN' ? 'green' : 'default'}>{job.status}</Tag>}
          actions={[
            applied.has(job.id)
              ? <Button onClick={() => navigate(`/interview?sessionId=${sessionByJob[job.id]}`)}>Continue Prep</Button>
              : <Button loading={applying.has(job.id)} onClick={() => handleApply(job.id)}>Apply Now</Button>,
            ...(job.externalUrl
              ? [<Button type="link" icon={<LinkOutlined />} href={job.externalUrl} target="_blank" rel="noopener noreferrer">View posting</Button>]
              : []),
          ]}
        >
          <div style={{ display:'flex', flexWrap:'wrap', gap:8, marginBottom:8, color:'#666', fontSize:13 }}>
            {job.companyName && <span><BuildOutlined /> <strong>{job.companyName}</strong></span>}
            <span><EnvironmentOutlined /> {job.location?.trim() || 'Location not specified'}</span>
            <span>· {job.type.replace('_', ' ')}</span>
            {salary && <span>· {salary}</span>}
          </div>
          <div style={{ display:'flex', flexWrap:'wrap', gap:8, marginBottom:8, fontSize:13 }}>
            <Tag color={level ? 'purple' : 'default'}>{level ?? 'Level not stated'}</Tag>
            {posted && <Text type="secondary"><ClockCircleOutlined /> {posted}</Text>}
          </div>
          {match && match.skillMatches.length > 0 ? (
            <div style={{ marginBottom:8, fontSize:13 }}>
              <Text type="secondary">Your skills that fit: </Text>
              {match.skillMatches.map(s => <Tag key={s} color="green">{s}</Tag>)}
              {match.skillGaps.length > 0 && (
                <div style={{ marginTop:4 }}>
                  <Text type="secondary">Also asks for: </Text>
                  {match.skillGaps.slice(0, 5).map(s => <Tag key={s}>{s}</Tag>)}
                </div>
              )}
            </div>
          ) : (
            <div style={{ display:'flex', flexWrap:'wrap', gap:4, marginBottom:8 }}>
              {job.skills.slice(0,5).map(s => <Tag key={s} color="blue">{s}</Tag>)}
              {job.skills.length > 5 && <Tag>+{job.skills.length-5}</Tag>}
            </div>
          )}
          {via && <Text type="secondary" style={{ fontSize:12 }}>Via {via}</Text>}
        </Card>
      </Col>
    );
  };

  if (feedsLoading) return <Spin size="large" style={{ display:'block', margin:'3rem auto' }} />;
  if (feedsError) return <Alert type="error" showIcon message={feedsError.message} />;
  if (!feed && !user?.id) return <Empty description="No job categories are set up yet." />;

  const tabs = [
    ...(user?.id ? [{ key: RECOMMENDED, label: `Recommended for you${matches.length ? ` (${matches.length})` : ''}` }] : []),
    ...feeds.map(f => ({ key: f.key, label: f.label })),
  ];

  return (
    <div>
      <Title level={4}>Find Jobs</Title>

      <Tabs activeKey={recommended ? RECOMMENDED : feed?.key} onChange={changeFeed} items={tabs} />

      {recommended ? (
        <>
          {matchesLoading && <Spin size="large" style={{ display:'block', margin:'3rem auto' }} />}
          {matchesError && <Alert type="error" showIcon message={matchesError.message} style={{ marginBottom: 16 }} />}
          {!matchesLoading && !matchesError && matches.length === 0 && (
            <Empty description="No matches yet. Jobs that fit your resume appear here after you upload it.">
              <Button type="primary" onClick={() => navigate('/resume')}>Upload resume</Button>
            </Empty>
          )}
          {!matchesLoading && matches.length > 0 && (
            <>
              <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center', gap:12, flexWrap:'wrap', marginBottom:16 }}>
                <Text type="secondary">Your best {matches.length} matches from your resume, best first.</Text>
                <Button icon={<ReloadOutlined />} loading={refreshing} onClick={refreshMatches}>Refresh matches</Button>
              </div>
              <Row gutter={[16, 16]}>{matches.map(m => renderJob(m.job!, m))}</Row>
            </>
          )}
        </>
      ) : feed && (<>

      <div style={{ display:'flex', gap:12, marginBottom:16, flexWrap:'wrap' }}>
        <Input placeholder="Search by title, company, skills..." value={search}
               onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearch(e.target.value)}
               onPressEnter={() => setQuery(search.trim())} prefix={<SearchOutlined />} size="large"
               style={{ maxWidth:400, flex:'1 1 280px' }} />
        <Button size="large" onClick={() => setQuery(search.trim())}>Search</Button>
      </div>

      {!loading && !error && (
        <Text type="secondary" style={{ display:'block', marginBottom:16 }}>
          {totalElements} {totalElements === 1 ? 'job' : 'jobs'}{query && ` matching “${query}”`}
        </Text>
      )}

      {loading && <Spin size="large" style={{ display:'block', margin:'3rem auto' }} />}

      {!loading && error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      {!loading && !error && items.length === 0 && (
        <Empty description={query ? `No ${feed.label} jobs match “${query}”.` : feed.emptyMessage} />
      )}

      {!loading && (
        <Row gutter={[16, 16]}>
          {items.map((job: Job) => renderJob(job, matchByJob.get(job.id)))}
        </Row>
      )}

      {!loading && !error && totalElements > PAGE_SIZE && (
        <Pagination
          style={{ marginTop: 24, textAlign: 'center' }}
          current={page + 1}
          pageSize={PAGE_SIZE}
          total={totalElements}
          showSizeChanger={false}
          onChange={(p: number) => { load(p - 1); window.scrollTo({ top: 0, behavior: 'smooth' }); }}
        />
      )}
      </>)}
    </div>
  );
};

export default JobsPage;
