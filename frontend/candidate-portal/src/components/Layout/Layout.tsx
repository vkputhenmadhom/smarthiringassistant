import React from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { useMutation, useQuery, useSubscription } from '@apollo/client';
import { Layout as AntLayout, Menu, Avatar, Dropdown, Badge, Button, Drawer, Grid, Popover, List, Typography, Empty } from 'antd';
import {
  DashboardOutlined, SearchOutlined, FileTextOutlined,
  UserOutlined, LogoutOutlined, ReadOutlined, BellOutlined, MenuOutlined
} from '@ant-design/icons';
import { logout } from '../../store/authSlice';
import { RootState } from '../../store';
import { MY_NOTIFICATIONS_QUERY, NEW_NOTIFICATION_SUBSCRIPTION, MARK_ALL_NOTIFICATIONS_READ_MUTATION, Notification } from '../../graphql';

const { Header, Sider, Content } = AntLayout;

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
  { key: '/jobs',      icon: <SearchOutlined />,    label: 'Find Jobs' },
  { key: '/resume',    icon: <FileTextOutlined />,  label: 'My Resume' },
  { key: '/interview', icon: <ReadOutlined />,      label: 'Interview Prep' },
];

const Layout: React.FC = () => {
  const { useBreakpoint } = Grid;
  const screens = useBreakpoint();
  const isMobile = !screens.md;

  const navigate  = useNavigate();
  const dispatch  = useDispatch();
  const user      = useSelector((s: RootState) => s.auth.user);
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  const [collapsed, setCollapsed] = React.useState(false);
  const [liveNotifications, setLiveNotifications] = React.useState<Notification[]>([]);

  React.useEffect(() => {
    setLiveNotifications([]);
  }, [user?.id]);

  // Shortlist and hire decisions are stored by notification-service; check for new ones every minute
  const { data: notificationData, refetch: refetchNotifications } = useQuery<{ myNotifications: Notification[] }>(MY_NOTIFICATIONS_QUERY, {
    variables: { unreadOnly: false },
    skip: !user?.id,
    fetchPolicy: 'cache-and-network',
    pollInterval: 60_000,
  });
  const [markAllRead] = useMutation(MARK_ALL_NOTIFICATIONS_READ_MUTATION);
  const notifications = notificationData?.myNotifications ?? [];

  const openNotifications = (open: boolean) => {
    // Opening the bell counts as reading what is in it
    if (!open && notifications.some(n => !n.read)) {
      markAllRead().then(() => { setLiveNotifications([]); refetchNotifications(); }).catch(() => undefined);
    }
  };

  const notificationPanel = (
    <div style={{ width: 340, maxHeight: 420, overflowY: 'auto' }}>
      {notifications.length === 0
        ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No notifications yet" />
        : <List
            dataSource={notifications}
            renderItem={(n: Notification) => (
              <List.Item style={{ background: n.read ? undefined : '#f0f5ff', padding: '10px 8px' }}>
                <List.Item.Meta
                  title={<Typography.Text strong={!n.read}>{n.title}</Typography.Text>}
                  description={<>
                    <div>{n.message}</div>
                    {n.createdAt && <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {new Date(n.createdAt).toLocaleString()}
                    </Typography.Text>}
                  </>}
                />
              </List.Item>
            )}
          />}
    </div>
  );

  useSubscription<{ newNotification: Notification }>(NEW_NOTIFICATION_SUBSCRIPTION, {
    variables: { userId: user?.id ?? '' },
    skip: !user?.id,
    onData: ({ data }) => {
      const notification = data.data?.newNotification;
      if (!notification) {
        return;
      }
      setLiveNotifications(previous =>
        previous.some(item => item.id === notification.id)
          ? previous
          : [notification, ...previous]
      );
    },
  });

  const unreadNotificationCount = React.useMemo(() => {
    const notifications = [
      ...(notificationData?.myNotifications ?? []),
      ...liveNotifications,
    ].filter(notification => !notification.read);

    return new Set(notifications.map(notification => notification.id)).size;
  }, [notificationData?.myNotifications, liveNotifications]);

  const userMenu = {
    items: [
      { key: 'profile', icon: <UserOutlined />,  label: 'Profile' },
      { key: 'logout',  icon: <LogoutOutlined />, label: 'Sign Out',
        onClick: () => { dispatch(logout()); navigate('/login'); } },
    ],
  };

  return (
    <AntLayout style={{ minHeight: '100vh' }}>
      {!isMobile && (
        <Sider
          width={220}
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          style={{ background: '#001529' }}
        >
          <div style={{ padding: '20px 16px', color: '#fff' }}>
            <h2 style={{ color: '#fff', margin: 0, fontSize: collapsed ? '.95rem' : '1.1rem' }}>SHA Candidate</h2>
            {!collapsed && <p style={{ color: '#aaa', fontSize: '.75rem', margin: '4px 0 0' }}>Career Portal</p>}
          </div>
          <Menu
            theme="dark"
            mode="inline"
            selectedKeys={[window.location.pathname]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
      )}

      <Drawer
        title="SHA Candidate"
        placement="left"
        width={260}
        onClose={() => setDrawerOpen(false)}
        open={isMobile && drawerOpen}
        styles={{ body: { padding: 0 } }}
      >
        <Menu
          mode="inline"
          selectedKeys={[window.location.pathname]}
          items={menuItems}
          onClick={({ key }) => {
            navigate(key);
            setDrawerOpen(false);
          }}
        />
      </Drawer>

      <AntLayout>
        <Header style={{ background: '#fff', display: 'flex', alignItems: 'center',
                         justifyContent: 'flex-end', gap: 16, padding: '0 16px',
                         boxShadow: '0 2px 8px rgba(0,0,0,.08)' }}>
          {isMobile && (
            <Button
              type="text"
              icon={<MenuOutlined style={{ fontSize: 18 }} />}
              onClick={() => setDrawerOpen(true)}
            />
          )}
          <Popover content={notificationPanel} title="Notifications" trigger="click" placement="bottomRight"
                   onOpenChange={openNotifications}>
            <Badge count={unreadNotificationCount} size="small">
              <Button type="text" aria-label="Notifications" icon={<BellOutlined style={{ fontSize: 18 }} />} />
            </Badge>
          </Popover>
          <Dropdown menu={userMenu} placement="bottomRight">
            <Avatar style={{ cursor: 'pointer', background: '#3f51b5' }}>
              {user?.username?.[0]?.toUpperCase() ?? 'U'}
            </Avatar>
          </Dropdown>
        </Header>
        <Content style={{ margin: isMobile ? 12 : 24, background: '#f5f6fa' }}>
          <Outlet />
        </Content>
      </AntLayout>
    </AntLayout>
  );
};

export default Layout;
