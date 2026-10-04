import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate } from 'react-router';
import { Loading } from '../components';
import Workspace, { AdminOnly, TeacherOnly, EditorOnly } from './admin/Workspace';
import Learning from './mastery/Learning';
import './platform.css';
const ContentList = lazy(() => import('./content/ContentList'));
const ContentEditor = lazy(() => import('./content/ContentEditor'));
const Users = lazy(() => import('./admin/Users'));
const Imports = lazy(() => import('./admin/Imports'));
const Groups = lazy(() => import('./teacher/Groups'));
const Submissions = lazy(() => import('./teacher/Submissions'));
const Courses = lazy(() => import('./courses/Courses'));
const Lesson = lazy(() => import('./courses/Courses').then((m) => ({ default: m.Lesson })));
const Assignments = lazy(() => import('./courses/Assignments'));
const Quiz = lazy(() => import('./courses/Quiz'));
const Search = lazy(() => import('./content/Search'));
const wait = (node: ReactNode) => <Suspense fallback={<Loading />}>{node}</Suspense>;
export const platformRoutes = [
  { path: '/search', element: wait(<Search />) },
  { path: '/learning', element: wait(<Learning />) },
  { path: '/learning/errors', element: wait(<Learning errors />) },
  { path: '/courses', element: wait(<Courses />) },
  { path: '/courses/:id', element: wait(<Courses />) },
  { path: '/lessons/:id', element: wait(<Lesson />) },
  { path: '/assignments', element: wait(<Assignments />) },
  { path: '/assignments/:id', element: wait(<Assignments />) },
  { path: '/quizzes/:id', element: wait(<Quiz />) },
  { path: '/quiz-attempts/:attemptId', element: wait(<Quiz />) },
  { path: '/admin', element: <Navigate to="/workspace/content" replace /> },
  { path: '/teacher', element: <Navigate to="/workspace/content" replace /> },
  {
    path: '/workspace',
    element: wait(<Workspace />),
    children: [
      { index: true, element: <Navigate to="content" replace /> },
      { path: 'content', element: wait(<ContentList />) },
      { path: 'content/new', element: wait(<ContentEditor />) },
      { path: 'content/:id', element: wait(<ContentEditor />) },
      { element: <AdminOnly />, children: [{ path: 'users', element: wait(<Users />) }] },
      { element: <EditorOnly />, children: [{ path: 'imports', element: wait(<Imports />) }] },
      {
        element: <TeacherOnly />,
        children: [
          { path: 'groups', element: wait(<Groups />) },
          { path: 'groups/:id', element: wait(<Groups />) },
          { path: 'assignments/:id', element: wait(<Submissions />) },
        ],
      },
    ],
  },
];
