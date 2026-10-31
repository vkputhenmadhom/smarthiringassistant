import React, { useEffect, useState } from 'react';
import { Alert, Card, Input, Row, Col, Tag, Button, Spin, Empty, Typography, Tabs, Pagination, message } from 'antd';
import { SearchOutlined, EnvironmentOutlined, BuildOutlined, LinkOutlined } from '@ant-design/icons';
import { useDispatch, useSelector } from 'react-redux';
import { useMutation } from '@apollo/client';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { fetchJobs } from '../../store/jobsSlice';
import { RootState, AppDispatch } from '../../store';
import { Job, START_SCREENING_MUTATION } from '../../graphql';

const { Title, Text } = Typography;

const PAGE_SIZE = 20;

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

/** Each tab is one job feed; `source` is the feed name the job service filters on. */
const FEEDS = [
  { key: 'tech',   label: 'India · Tech',            source: 'INDIA_TECH', empty: 'No India tech jobs yet. New ones are imported daily.' },
  { key: 'remote', label: 'Remote · open to India',  source: 'REMOTE',     empty: 'No remote jobs open to India yet. New ones are imported daily.' },
  { key: 'govt',   label: 'India · Government',      source: 'INDIA_GOVT', empty: 'No government jobs yet. New ones are imported daily.' },
  { key: 'posted', label: 'Posted here',             source: 'POSTED',     empty: 'No jobs have been posted on this site yet.' },
] as const;

type FeedKey = typeof FEEDS[number]['key'];

const feedFor = (key: string | null) => FEEDS.find(f => f.key === key) ?? FEEDS[0];

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
  const feed = feedFor(params.get('feed'));
  const { items, loading, totalElements, page, error } = useSelector((s: RootState) => s.jobs);
  const user = useSelector((s: RootState) => s.auth.user);
  const [search, setSearch]   = useState('');
  const [query, setQuery]     = useState('');
  const [applied, setApplied] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState<Set<string>>(new Set());
  const [sessionByJob, setSessionByJob] = useState<Record<string, string>>({});

  const [startScreening] = useMutation<StartScreeningResponse>(START_SCREENING_MUTATION);

  const load = (pageIndex: number) =>
    dispatch(fetchJobs({ source: feed.source, search: query || undefined, page: pageIndex, size: PAGE_SIZE }));

  useEffect(() => { load(0); }, [dispatch, feed.source, query]); // eslint-disable-line react-hooks/exhaustive-deps

  const changeFeed = (key: string) => setParams({ feed: key as FeedKey }, { replace: true });

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

  return (
    <div>
      <Title level={4}>Find Jobs</Title>

      <Tabs
        activeKey={feed.key}
        onChange={changeFeed}
        items={FEEDS.map(f => ({ key: f.key, label: f.label }))}
      />

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
        <Empty description={query ? `No ${feed.label} jobs match “${query}”.` : feed.empty} />
      )}

      {!loading && (
        <Row gutter={[16, 16]}>
          {items.map((job: Job) => {
            const salary = salaryText(job);
            const via = sourceLabel(job.source);
            return (
              <Col xs={24} md={12} key={job.id}>
                <Card
                  hoverable
                  title={job.title}
                  extra={<Tag color={job.status === 'OPEN' ? 'green' : 'default'}>{job.status}</Tag>}
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
                  <div style={{ display:'flex', flexWrap:'wrap', gap:4, marginBottom:8 }}>
                    {job.skills.slice(0,5).map(s => <Tag key={s} color="blue">{s}</Tag>)}
                    {job.skills.length > 5 && <Tag>+{job.skills.length-5}</Tag>}
                  </div>
                  {via && <Text type="secondary" style={{ fontSize:12 }}>Via {via}</Text>}
                </Card>
              </Col>
            );
          })}
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
    </div>
  );
};

export default JobsPage;
